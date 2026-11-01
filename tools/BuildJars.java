import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class BuildJars {
    public static void main(String[] args) throws IOException {
        if (args.length < 3 || args.length > 5) { throw new IllegalArgumentException("Usage: BuildJars <classes> <dist> <resources> [lib] [both|client]"); }
        Path classes = Path.of(args[0]), dist = Path.of(args[1]), resources = Path.of(args[2]);
        if (args.length == 5 && "client".equals(args[4])) {
            build(classes, dist.resolve("TreasureRushClient.jar"), "client.GameClient", "client", resources, "");
            return;
        }
        if (args.length == 5 && !"both".equals(args[4])) { throw new IllegalArgumentException("Target must be both or client"); }
        java.util.List<Path> drivers = new java.util.ArrayList<>();
        if (args.length >= 4 && Files.isDirectory(Path.of(args[3]))) {
            try (java.util.stream.Stream<Path> paths = Files.list(Path.of(args[3]))) {
                drivers.addAll(paths.filter(p -> p.getFileName().toString().matches("mysql-connector-j-[A-Za-z0-9.-]+\\.jar")).toList());
            }
        }
        if (drivers.size() > 1) { throw new IOException("Keep only one MySQL Connector/J version in lib"); }
        for (Path driver : drivers) {
            Path destination = dist.resolve("lib").resolve(driver.getFileName());
            Files.createDirectories(destination.getParent());
            Files.copy(driver, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        String driverClassPath = String.join(" ", drivers.stream().map(p -> "lib/" + p.getFileName()).toList());
        build(classes, dist.resolve("TreasureRushClient.jar"), "client.GameClient", "client", resources, "");
        build(classes, dist.resolve("TreasureRushServer.jar"), "server.GameServer", "server", resources, driverClassPath);
    }
    private static void build(Path classes, Path output, String main, String ownPackage, Path resources, String classPath) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, main);
        if (!classPath.isEmpty()) { manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPath); }
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output), manifest)) {
            for (String packageName : new String[] {ownPackage, "common", "model"}) {
                try (java.util.stream.Stream<Path> paths = Files.walk(classes.resolve(packageName))) {
                    for (Path file : paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class")).sorted().toList()) {
                        jar.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                        Files.copy(file, jar); jar.closeEntry();
                    }
                }
            }
            if (ownPackage.equals("client")) {
                for (String name : new String[] {"adventure", "pickup", "gold", "trap", "win", "lose",
                        "lobby_bgm", "treasure_pickup", "treasure_gold", "trap_stun", "ui_click"}) {
                    Path file = resources.resolve("audio").resolve(name + ".wav");
                    jar.putNextEntry(new JarEntry("audio/" + name + ".wav"));
                    Files.copy(file, jar); jar.closeEntry();
                }
            }
        }
    }
}
