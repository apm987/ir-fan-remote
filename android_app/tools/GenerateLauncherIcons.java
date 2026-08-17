import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Generates Android launcher icon density variants from artwork/icon-source.png. */
public final class GenerateLauncherIcons {
    private static final Map<String, Integer> LEGACY_SIZES = new LinkedHashMap<>();
    private static final Map<String, Integer> ADAPTIVE_SIZES = new LinkedHashMap<>();

    static {
        LEGACY_SIZES.put("mdpi", 48);
        LEGACY_SIZES.put("hdpi", 72);
        LEGACY_SIZES.put("xhdpi", 96);
        LEGACY_SIZES.put("xxhdpi", 144);
        LEGACY_SIZES.put("xxxhdpi", 192);

        ADAPTIVE_SIZES.put("mdpi", 108);
        ADAPTIVE_SIZES.put("hdpi", 162);
        ADAPTIVE_SIZES.put("xhdpi", 216);
        ADAPTIVE_SIZES.put("xxhdpi", 324);
        ADAPTIVE_SIZES.put("xxxhdpi", 432);
    }

    private GenerateLauncherIcons() {
    }

    public static void main(String[] args) throws IOException {
        Path project = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath();
        Path sourcePath = project.resolve("artwork/icon-source.png");
        BufferedImage source = ImageIO.read(sourcePath.toFile());
        if (source == null || source.getWidth() != source.getHeight()) {
            throw new IllegalArgumentException("El icono fuente debe ser un PNG cuadrado");
        }

        for (String density : LEGACY_SIZES.keySet()) {
            Path destination = project.resolve(
                    "app/src/main/res/mipmap-" + density
            );
            Files.createDirectories(destination);
            writeLegacy(source, LEGACY_SIZES.get(density), destination.resolve("ic_launcher.png"));
            writeScaled(
                    source,
                    ADAPTIVE_SIZES.get(density),
                    destination.resolve("ic_launcher_foreground.png")
            );
        }
    }

    private static void writeLegacy(BufferedImage source, int size, Path destination)
            throws IOException {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
        );
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );
        float inset = Math.max(1f, size * 0.025f);
        float diameter = size - (2f * inset);
        graphics.setClip(new RoundRectangle2D.Float(
                inset,
                inset,
                diameter,
                diameter,
                size * 0.34f,
                size * 0.34f
        ));
        graphics.drawImage(source, 0, 0, size, size, null);
        graphics.dispose();
        ImageIO.write(target, "png", destination.toFile());
    }

    private static void writeScaled(BufferedImage source, int size, Path destination)
            throws IOException {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
        );
        graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
        );
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );
        graphics.drawImage(source, 0, 0, size, size, null);
        graphics.dispose();
        ImageIO.write(target, "png", destination.toFile());
    }
}
