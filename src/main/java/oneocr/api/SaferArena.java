package oneocr.api;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.Charset;

public sealed abstract class SaferArena implements Arena {
    
    public static final class Global extends SaferArena {
        public Global() {super(Arena.global());}
        public static Global create(){return new Global();}
    }
    
    public static final class Shared extends SaferArena {
        public Shared() {super(Arena.ofShared());}
        public static Shared create(){return new Shared();}
    }
    
    public static final class Confined extends SaferArena {
        public Confined() {super(Arena.ofConfined());}
        public static Confined create(){return new Confined();}
    }
    
    public static final class Auto extends SaferArena {
        public Auto() {super(Arena.global());}
        public static Auto create(){return new Auto();}
    }
    

    final Arena arena;

    public SaferArena(Arena arena) {
        this.arena = arena;
    }
    
    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        return arena.allocate(byteSize, byteAlignment);
    }

    @Override
    public MemorySegment.Scope scope() {
        return arena.scope();
    }

    @Override
    public void close() {
        switch (this) {
            case Auto _ -> {} /*System.gc() ?*/
            case Confined c -> c.arena.close();
            case Global _ -> {}
            case Shared sh -> sh.arena.close();
        }
        
    }

    @Override
    public MemorySegment allocateFrom(String str) {
        return arena.allocateFrom(str);
    }

    @Override
    public MemorySegment allocateFrom(String str, Charset charset) {
        return arena.allocateFrom(str, charset);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfByte layout, byte value) {
        return arena.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfChar layout, char value) {
        return arena.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfShort layout, short value) {
        return arena.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfInt layout, int value) {
        return arena.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfFloat layout, float value) {
        return arena.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfLong layout, long value) {
        return arena.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfDouble layout, double value) {
        return arena.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(AddressLayout layout, MemorySegment value) {
        return arena.allocateFrom(layout, value);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout elementLayout, MemorySegment source, ValueLayout sourceElementLayout, long sourceOffset, long elementCount) {
        return arena.allocateFrom(elementLayout, source, sourceElementLayout, sourceOffset, elementCount);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfByte elementLayout, byte... elements) {
        return arena.allocateFrom(elementLayout, elements);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfShort elementLayout, short... elements) {
        return arena.allocateFrom(elementLayout, elements);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfChar elementLayout, char... elements) {
        return arena.allocateFrom(elementLayout, elements);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfInt elementLayout, int... elements) {
        return arena.allocateFrom(elementLayout, elements);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfFloat elementLayout, float... elements) {
        return arena.allocateFrom(elementLayout, elements);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfLong elementLayout, long... elements) {
        return arena.allocateFrom(elementLayout, elements);
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout.OfDouble elementLayout, double... elements) {
        return arena.allocateFrom(elementLayout, elements);
    }

    @Override
    public MemorySegment allocate(MemoryLayout layout) {
        return arena.allocate(layout);
    }

    @Override
    public MemorySegment allocate(MemoryLayout elementLayout, long count) {
        return arena.allocate(elementLayout, count);
    }

    @Override
    public MemorySegment allocate(long byteSize) {
        return arena.allocate(byteSize);
    }

}
