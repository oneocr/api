package oneocr.api;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Utils {
    // JDK compatibility flags and cached MethodHandles (class-level for performance)
    private static final boolean isJdk21 = isJdk21();
    private static final MethodHandle ALLOCATE_STRING_HANDLE;
    private static final MethodHandle READ_STRING_HANDLE;
    
        
    static {
        // Initialize MethodHandles once at class loading time for optimal performance
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        
        if (isJdk21) {
            try {
                // JDK21 - allocateUtf8String(String) - no charset parameter needed
                ALLOCATE_STRING_HANDLE = lookup.findVirtual(Arena.class, "allocateUtf8String", 
                    MethodType.methodType(MemorySegment.class, String.class));
                READ_STRING_HANDLE = lookup.findVirtual(MemorySegment.class, "getUtf8String", 
                    MethodType.methodType(String.class, long.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("JDK21 FFM methods not found - incompatible JDK21 version", e);
            }
        } else {
            try {
                // JDK22+ - Try charset version first, fallback to simple version
                MethodHandle allocateHandle = null;
                try {
                    // Try the charset version first (more explicit)
                    allocateHandle = lookup.findVirtual(Arena.class, "allocateFrom", 
                        MethodType.methodType(MemorySegment.class, String.class, java.nio.charset.Charset.class));
                } catch (NoSuchMethodException e1) {
                    // Fallback to non-charset version
                    allocateHandle = lookup.findVirtual(Arena.class, "allocateFrom", 
                        MethodType.methodType(MemorySegment.class, String.class));
                }
                ALLOCATE_STRING_HANDLE = allocateHandle;
                
                READ_STRING_HANDLE = lookup.findVirtual(MemorySegment.class, "getString", 
                    MethodType.methodType(String.class, long.class, java.nio.charset.Charset.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("JDK22+ FFM methods not found", e);
            }
        }
    }

    static boolean isJdk21() {
        var version = System.getProperty("java.version");
        return version.startsWith("21.");
    }
    
    // JDK21/22+ compatibility methods using cached MethodHandles for performance
    static MemorySegment allocateString(Arena arena, String text) {
        try {
            if (isJdk21) {
                // JDK21: allocateUtf8String(String) - only text parameter
                return (MemorySegment) ALLOCATE_STRING_HANDLE.invoke(arena, text);
            } else {
                // JDK22+: May need charset parameter depending on which method was found
                try {
                    // Try with charset first (if that's what was found)
                    return (MemorySegment) ALLOCATE_STRING_HANDLE.invoke(arena, text, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Throwable e1) {
                    // Fallback to no charset version
                    return (MemorySegment) ALLOCATE_STRING_HANDLE.invoke(arena, text);
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to allocate string using cached MethodHandle", t);
        }
    }
    
    static String readString(long address) {
        if (address == 0) return "";
        
        var segment = MemorySegment.ofAddress(address).reinterpret(Long.MAX_VALUE);
        
        try {
            if (isJdk21) {
                // JDK21: getUtf8String(long) - no charset parameter
                return (String) READ_STRING_HANDLE.invoke(segment, 0L);
            } else {
                // JDK22+: getString(long, Charset) - requires explicit charset
                return (String) READ_STRING_HANDLE.invoke(segment, 0L, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to read string using cached MethodHandle", t);
        }
    }
}
