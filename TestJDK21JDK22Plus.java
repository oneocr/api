///usr/bin/env java "$0" "$@" ; exit $?
// Standalone Java code (not part of main project) - replaces bash/python/batch scripts with IDE-friendly, maintainable code using JDK 11/21/25 enhancements. To know why, refer to Cay Horstmann's JavaOne 2025 talk "Java for Small Coding Tasks" (https://youtu.be/04wFgshWMdA)
//DESCRIPTION Performance test for both JDK versions

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

// run with jbang or just plain java directly from this source file
class TestJDK21JDK22Plus {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== JDK Performance Test Setup ===");
        
        // Find all JDK installations
        List<Path> jdkPaths = findJdkInstallations();
        
        if (jdkPaths.isEmpty()) {
            System.err.println("ERROR: No valid JDK installations found");
            System.exit(1);
        }
        
        System.out.println("Found JDKs:");
        for (Path jdk : jdkPaths) {
            System.out.println("  - " + jdk.getFileName() + " at " + jdk);
        }
        
        // Find JDK 21 and JDK 22+ (22+ means jdk22 or above)
        Path jdk21 = findJdkVersion(jdkPaths, "21");
        Path jdk22plus = findJdkVersion(jdkPaths, "2[2-9]|[3-9][0-9]");
        
        if (jdk21 == null) {
            System.err.println("ERROR: JDK-21 not found");
            showAvailableJdks(jdkPaths);
            System.exit(1);
        }
        
        if (jdk22plus == null) {
            System.err.println("ERROR: JDK-22+ not found");  
            showAvailableJdks(jdkPaths);
            System.exit(1);
        }
        
        System.out.println("\n✓ Selected JDK-21: " + jdk21);
        System.out.println("✓ Selected JDK-22+: " + jdk22plus);
        
        // Validate JDKs
        validateJdk(jdk21, "21");
        validateJdk(jdk22plus, "22+");
        
        // Check test image
        Path testImage = Paths.get("src/test/resources/ocr-book.jpg");
        if (!Files.exists(testImage)) {
            System.err.println("ERROR: Test image " + testImage + " not found");
            System.exit(1);
        }
        
        System.out.println("\n=== Starting Performance Tests ===");
        
        // Build and test JDK 21
        buildAndTest(jdk21, "build-jdk21", "21", testImage, true);
        
        // Build and test JDK 22+  (22+ means jdk22 or above)
        buildAndTest(jdk22plus, "build-jdk22", "22+", testImage, false);
        
        System.out.println("\n=== Performance Comparison Complete ===");
    }
    
    static List<Path> findJdkInstallations() throws Exception {
        List<Path> jdks = new ArrayList<>();
        
        // Find java.exe in PATH first
        String javaExe = findJavaInPath();
        if (javaExe != null) {
            Path javaHome = Paths.get(javaExe).getParent().getParent();
            if (isValidJdk(javaHome)) {
                jdks.add(javaHome);
                System.out.println("Current Java from PATH: " + javaHome);
                
                // Search parent directory for other JDKs
                Path parent = javaHome.getParent();
                if (parent != null) {
                    try {
                        Files.list(parent)
                            .filter(p -> p.getFileName().toString().toLowerCase().contains("jdk"))
                            .filter(TestJDK21JDK22Plus::isValidJdk)
                            .forEach(jdks::add);
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            }
        }
        
        // Check JAVA_HOME
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            Path javaHomePath = Paths.get(javaHome);
            if (isValidJdk(javaHomePath)) {
                jdks.add(javaHomePath);
                System.out.println("JAVA_HOME: " + javaHomePath);
            }
        }
        
		// We are not even trying linux/mac because anyway this is a windows only library as of now
        // Search common installation paths
        String[] searchPaths = {
            "C:\\Program Files\\Java",
            "C:\\Program Files\\Eclipse Adoptium", 
            "C:\\Program Files\\Microsoft",
            "C:\\Program Files\\Amazon Corretto"
        };
        
        for (String searchPath : searchPaths) {
            Path path = Paths.get(searchPath);
            if (Files.exists(path)) {
                try {
                    Files.list(path)
                        .filter(p -> p.getFileName().toString().toLowerCase().contains("jdk"))
                        .filter(TestJDK21JDK22Plus::isValidJdk)
                        .forEach(jdks::add);
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        
        // Remove duplicates
        return jdks.stream().distinct().sorted().toList();
    }
    
    static String findJavaInPath() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        
        String[] paths = pathEnv.split(File.pathSeparator);
        for (String path : paths) {
            Path javaExe = Paths.get(path, "java.exe");
            if (Files.exists(javaExe)) {
                System.out.println("Found Java in PATH: " + javaExe);
                return javaExe.toString();
            }
        }
        return null;
    }
    
    static boolean isValidJdk(Path jdkPath) {
        if (!Files.isDirectory(jdkPath)) return false;
        Path javaExe = jdkPath.resolve("bin").resolve("java.exe");
        return Files.exists(javaExe);
    }
    
    static Path findJdkVersion(List<Path> jdks, String versionRegex) {
        Pattern pattern = Pattern.compile("jdk-?" + versionRegex + "(\\..*)?$", Pattern.CASE_INSENSITIVE);
        return jdks.stream()
            .filter(jdk -> pattern.matcher(jdk.getFileName().toString()).find())
            .findFirst()
            .orElse(null);
    }
    
    static void showAvailableJdks(List<Path> jdks) {
        System.out.println("Available JDKs:");
        for (Path jdk : jdks) {
            System.out.println("  - " + jdk.getFileName());
        }
    }
    
    static String findMavenCommand() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        
        String[] paths = pathEnv.split(File.pathSeparator);
        for (String path : paths) {
            Path mvnCmd = Paths.get(path, "mvn.cmd");
            if (Files.exists(mvnCmd)) {
                return mvnCmd.toString();
            }
            Path mvnBat = Paths.get(path, "mvn.bat");
            if (Files.exists(mvnBat)) {
                return mvnBat.toString();
            }
            Path mvnExe = Paths.get(path, "mvn.exe");
            if (Files.exists(mvnExe)) {
                return mvnExe.toString();
            }
        }
        return null;
    }
    
    static void validateJdk(Path jdkPath, String version) throws Exception {
        Path javaExe = jdkPath.resolve("bin").resolve("java.exe");
        ProcessBuilder pb = new ProcessBuilder(javaExe.toString(), "-version");
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String versionLine = reader.readLine();
            if (versionLine != null) {
                System.out.println("✓ JDK-" + version + " validated: " + jdkPath);
                System.out.println("  Version: " + versionLine);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("✗ JDK-" + version + " validation failed: " + jdkPath);
            System.exit(1);
        }
    }
    
    static void buildAndTest(Path jdkPath, String profile, String version, Path testImage, boolean enablePreview) throws Exception {
        System.out.println("\n=== Building JDK-" + version + " version ===");
        
        // Set JAVA_HOME and build
        String mvnCmd = findMavenCommand();
        if (mvnCmd == null) {
            System.err.println("✗ Maven not found in PATH");
            return;
        }
        ProcessBuilder mvnBuild = new ProcessBuilder(mvnCmd, "clean", "compile", "test-compile", "-P", profile, "-q");
        mvnBuild.environment().put("JAVA_HOME", jdkPath.toString());
        Process buildProcess = mvnBuild.start();
        int buildExit = buildProcess.waitFor();
        
        if (buildExit != 0) {
            System.err.println("✗ Build failed for JDK-" + version);
            return;
        }
        
        System.out.println("\n=== JDK-" + version + " Performance Test ===");
        
        // Run performance test
        Path javaExe = jdkPath.resolve("bin").resolve("java.exe");
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe.toString());
        if (enablePreview) {
            cmd.add("--enable-preview");
        }
        cmd.add("--enable-native-access=oneocr.api");
        cmd.add("-cp");
        cmd.add("target/classes;target/test-classes");
        cmd.add("oneocr.api.JavaBenchmark");
        cmd.add(testImage.toString());
        cmd.add("5");
        
        ProcessBuilder testRun = new ProcessBuilder(cmd);
        testRun.environment().put("JAVA_HOME", jdkPath.toString());
        testRun.inheritIO();
        Process testProcess = testRun.start();
        int testExit = testProcess.waitFor();
        
        if (testExit == 0) {
            System.out.println("✓ JDK-" + version + " test completed successfully");
        } else {
            System.err.println("✗ JDK-" + version + " test failed with exit code: " + testExit);
        }
    }
}