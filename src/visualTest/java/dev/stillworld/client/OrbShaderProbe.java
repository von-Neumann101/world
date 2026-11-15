package dev.stillworld.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Renders the shipped orb shader in a hidden, unfocused context without starting Minecraft. */
public final class OrbShaderProbe {
    private static int width = 640, height = 360;
    private static final float FOV = 70, DISTANCE = 6;
    private static final String VERTEX = """
            #version 150
            out vec2 texCoord;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                texCoord = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private final int frozen = texture(GL_RGBA32F, GL_RGBA);
    private final int reality = texture(GL_RGBA32F, GL_RGBA);
    private final int depth = texture(GL_R32F, GL_RED);
    private final int target = texture(GL_RGBA32F, GL_RGBA);
    private final int framebuffer = glGenFramebuffers();
    private final StringBuilder report = new StringBuilder();
    private int program, checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 4)
            throw new IllegalArgumentException("shader directory, evidence directory, optional width height");
        if (args.length == 4) {
            width = Integer.parseInt(args[2]);
            height = Integer.parseInt(args[3]);
        }
        if (width < 64 || height < 64) throw new IllegalArgumentException("Viewport must be at least 64 by 64");
        try (var errors = GLFWErrorCallback.createPrint(System.err)) {
            errors.set();
            if (!glfwInit()) throw new AssertionError("GLFW initialization failed");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            long window = glfwCreateWindow(width, height, "Stillworld invisible orb shader probe", 0, 0);
            if (window == 0) throw new AssertionError("Hidden context creation failed");
            try {
                glfwMakeContextCurrent(window);
                GL.createCapabilities();
                glBindVertexArray(glGenVertexArrays());
                if (glfwGetWindowAttrib(window, GLFW_VISIBLE) != GLFW_FALSE
                        || glfwGetWindowAttrib(window, GLFW_FOCUSED) != GLFW_FALSE
                        || glfwGetInputMode(window, GLFW_CURSOR) != GLFW_CURSOR_NORMAL)
                    throw new AssertionError("Probe must remain hidden, unfocused and use the ordinary cursor");
                new OrbShaderProbe().run(Path.of(args[0]), Path.of(args[1]).resolve(width + "x" + height));
            } finally {
                glfwDestroyWindow(window);
                glfwTerminate();
                glfwSetErrorCallback(null);
            }
        }
    }

    private void run(Path shaders, Path output) throws Exception {
        Files.createDirectories(output);
        Files.writeString(output.resolve("result.txt"), "RUNNING: hidden orb shader validation\n");
        program = program(Files.readString(shaders.resolve("reality_orb.fsh")));
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "complete offscreen framebuffer");
        Matrix4f view = projection(0, 0);
        Vector3f center = new Vector3f(0, 0, -DISTANCE);
        float[] base = scene(false), live = scene(true), far = plane(view, 100), near = plane(view, 2);
        float[] visible = render(base, live, far, view, center, 1, 1);
        double radius = projectedRadius(DISTANCE);

        // Constant input isolates actual silhouette movement from live-world animation.
        float[] contourBase=solid(.32f,.32f,.32f),contourLive=solid(.70f,.40f,.15f);
        float[] contour0=render(contourBase,contourLive,far,view,center,1,1,0);
        float[] contour1=render(contourBase,contourLive,far,view,center,1,1,.8f);
        float[] contourNext=render(contourBase,contourLive,far,view,center,1,1,1f/60);
        double[] outline0=outline(contourBase,contour0,radius),outline1=outline(contourBase,contour1,radius);
        double min=java.util.Arrays.stream(outline0).min().orElseThrow(),max=java.util.Arrays.stream(outline0).max().orElseThrow();
        check(max-min>radius*.08,"silhouette varies by direction instead of remaining a perfect circle");
        check(meanDifference(outline0,outline1)>radius*.015,"constant live input still has a continuously moving boundary");
        check(meanDifference(outline0,outline(contourBase,contourNext,radius))<radius*.012+.25,
                "adjacent animation frames move smoothly without a contour jump");
        check(diskDifference(contourLive,contour1,radius*.5)<.00001,"boundary motion leaves the central live image stable");
        write(output.resolve("contour-0.png"),contour0);write(output.resolve("contour-08.png"),contour1);

        // The central patch must contain the actual texture: a no-op, gray conversion or global
        // color-filter implementation fails here even when its silhouette looks plausible.
        check(diskDifference(live, visible, radius * .35) < .00001, "central real-world texture retains its original RGB");
        int mid = ((height / 2) * width + width / 2) * 4;
        check(visible[mid + 1] - visible[mid + 2] > .15, "orb interior remains visibly colored");
        check(outerDifference(base, visible, radius * 1.15) < .00001, "pixels outside the maximum rippled boundary are preserved");
        check(diskDifference(base, visible, radius * .35) > .1, "sphere replaces frozen pixels with the live scene");
        check(allFiniteOpaque(visible), "visible sphere is finite and fully opaque");
        check(maxDifference(base, render(base, live, far, view, center, 1, 0)) < .00001, "zero opacity preserves the frozen scene");
        check(maxDifference(base, render(base, live, near, view, center, 1, 1)) < .00001, "nearer frozen geometry occludes the entire sphere");
        check(diskDifference(live, render(base, live, plane(view, 12), view, center, 1, 1), radius * .35) < .00001,
                "farther geometry reveals the real-world color again");

        float[] partialDepth = far.clone();
        for (int y = 0; y < height; y++) for (int x = 0; x < width / 2; x++) partialDepth[y * width + x] = near[y * width + x];
        float[] partial = render(base, live, partialDepth, view, center, 1, 1);
        check(rectDifference(base, partial, 0, 0, width / 2, height) < .00001, "foreground occludes only its covered half of the sphere");
        check(rectDifference(visible, partial, width / 2, 0, width, height) < .00001, "unoccluded half retains the same live view");
        write(output.resolve("frozen.png"), base);
        write(output.resolve("live.png"), live);
        write(output.resolve("orb.png"), visible);
        write(output.resolve("foreground-occlusion.png"), partial);

        float[] half = render(base, live, far, view, center, 1, .5f);
        check(blendError(base, visible, half) < .00001, "opacity blends only the sphere contribution");
        check(maxDifference(base, render(base, live, far, view, new Vector3f(0, 0, DISTANCE), 1, 1)) < .00001,
                "sphere behind the eye cannot appear in front");
        check(maxDifference(base, render(base, live, far, view, new Vector3f(100, 0, -DISTANCE), 1, 1)) < .00001,
                "offscreen sphere does not tint the viewport");

        // Uniformly colored live input isolates surface treatment from scene texture edges.
        float[] solid = solid(.70f, .40f, .15f);
        float[] shaded = render(base, solid, far, view, center, 1, 1);
        check(diskDifference(solid, shaded, radius * .5) < .00001, "dark rim and silver ring leave the central half untouched");
        check(annulusMean(shaded, radius * .86, radius * .93, 0) < .45, "grazing surface darkens to give the orb spherical volume");
        check(ringFraction(shaded, radius) < .12, "silver edge remains a thin accent rather than dominating the interior");
        write(output.resolve("surface-treatment.png"), shaded);

        for (float distance : new float[]{1.001f, 1.05f, 1.5f, .5f, 0}) {
            float[] pixels = render(base, live, far, view, new Vector3f(0, 0, -distance), 1, 1);
            check(allFiniteOpaque(pixels), "finite near-surface or inside-eye rendering at distance " + distance);
        }
        for (float yaw : new float[]{-80, 35, 170}) {
            Matrix4f rotated = projection(yaw, 18);
            Vector3f rotatedCenter = new Matrix4f().rotateY((float) Math.toRadians(-yaw))
                    .rotateX((float) Math.toRadians(-18)).transformPosition(new Vector3f(0, 0, -DISTANCE));
            float[] pixels = render(base, live, plane(rotated, 100), rotated, rotatedCenter, 1, 1);
            check(diskDifference(live,pixels,radius*.35)<.00001&&outerDifference(base,pixels,radius*1.15)<.00001,
                    "camera rotation keeps the live center and bounded world-space silhouette at yaw " + yaw);
        }
        float edgeX = (float) (DISTANCE * Math.tan(Math.toRadians(FOV / 2)) * width / height);
        float[] edge = render(base, live, far, view, new Vector3f(edgeX, 0, -DISTANCE), 1, 1);
        check(allFiniteOpaque(edge), "partially clipped sphere has finite samples at the frame edge");
        check(rectDifference(base, edge, 0, 0, width / 2, height) < .00001, "frame-edge sphere leaves the opposite half unchanged");
        check(rectDifference(base, edge, width * 3 / 4, 0, width, height) > .01, "partially clipped sphere stays visible");
        write(output.resolve("frame-edge.png"), edge);
        checkHud(base,live,near,view,center,output);
        int error = glGetError();
        check(error == GL_NO_ERROR, "OpenGL error state is clear: " + error);
        Files.writeString(output.resolve("result.txt"), "PASS " + checks + " checks\nGPU: " + glGetString(GL_RENDERER) + "\n" + report);
        System.out.println("ORB SHADER PASS: " + checks + " checks; hidden unfocused context; evidence " + output);
    }

    private float[] render(float[] base, float[] live, float[] depths, Matrix4f matrix, Vector3f center, float radius, float opacity) {
        return render(base,live,depths,matrix,center,radius,opacity,0);
    }
    private float[] render(float[] base, float[] live, float[] depths, Matrix4f matrix, Vector3f center, float radius, float opacity,float time) {
        return render(base,live,depths,matrix,center,radius,opacity,time,false,0);
    }
    private float[] render(float[] base, float[] live, float[] depths, Matrix4f matrix, Vector3f center, float radius, float opacity,float time,boolean hud,float hudRadius) {
        return render(base,live,depths,matrix,center,radius,opacity,time,hud?1:0,hudRadius,1,0,0);
    }
    private float[] render(float[] base, float[] live, float[] depths, Matrix4f matrix, Vector3f center, float radius, float opacity,float time,int hudMode,float hudRadius,float hudOpacity,float far,float exit) {
        upload(frozen, GL_RGBA, base);
        upload(reality, GL_RGBA, live);
        upload(depth, GL_RED, depths);
        glUseProgram(program);
        int[] textures = {frozen, depth, reality};
        String[] names = {"DiffuseSampler", "DepthSampler", "RealitySampler"};
        for (int i = 0; i < textures.length; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            glBindTexture(GL_TEXTURE_2D, textures[i]);
            glUniform1i(glGetUniformLocation(program, names[i]), i);
        }
        glUniform2f(glGetUniformLocation(program, "InSize"), width, height);
        glUniformMatrix4fv(glGetUniformLocation(program, "InverseViewProjection"), false, matrix.invert(new Matrix4f()).get(new float[16]));
        glUniformMatrix4fv(glGetUniformLocation(program, "ViewProjection"), false, matrix.get(new float[16]));
        glUniform3f(glGetUniformLocation(program, "SphereCenter"), center.x, center.y, center.z);
        glUniform1f(glGetUniformLocation(program, "SphereRadius"), radius);
        glUniform1f(glGetUniformLocation(program, "Opacity"), opacity);
        glUniform1f(glGetUniformLocation(program, "AnimationTime"), time);
        glUniform1f(glGetUniformLocation(program, "HudMode"), hudMode);
        glUniform2f(glGetUniformLocation(program, "HudCenter"), 41,height-77);
        glUniform1f(glGetUniformLocation(program, "HudRadius"), hudRadius);
        glUniform1f(glGetUniformLocation(program, "HudOpacity"), hudOpacity);
        glUniform1f(glGetUniformLocation(program, "HudFar"), far);
        glUniform1f(glGetUniformLocation(program, "HudWaveTime"), time*.4f);
        glUniform1f(glGetUniformLocation(program, "ExitProgress"), exit);
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glViewport(0, 0, width, height);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        float[] result = new float[width * height * 4];
        glReadPixels(0, 0, width, height, GL_RGBA, GL_FLOAT, result);
        return result;
    }

    private void checkHud(float[] base,float[] live,float[] depths,Matrix4f view,Vector3f center,Path output) throws Exception {
        float[] full=render(base,live,depths,view,center,0,0,0,true,28);
        float[] quarter=render(base,live,depths,view,center,0,0,0,true,14);
        float[] empty=render(base,live,depths,view,center,0,0,0,true,0);
        int changedFull=0,changedQuarter=0;
        double innerError=0,outerError=0;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int i=(y*width+x)*4;
            double distance=Math.hypot(x+.5-41,y+.5-(height-77));
            double fullChange=0,quarterChange=0;
            for(int c=0;c<3;c++) {
                fullChange+=Math.abs(base[i+c]-full[i+c]);
                quarterChange+=Math.abs(base[i+c]-quarter[i+c]);
                if(distance<10)innerError=Math.max(innerError,Math.abs(live[i+c]-full[i+c]));
                if(distance>28*1.15)outerError=Math.max(outerError,Math.abs(base[i+c]-full[i+c]));
            }
            if(fullChange>.01)changedFull++;
            if(quarterChange>.01)changedQuarter++;
        }
        check(innerError<.00001,"HUD reveals same-screen live reality even inside the world orb and behind foreground depth");
        check(changedFull>1500&&changedQuarter>300&&changedQuarter<changedFull*.3,"less energy shrinks the top-left live aperture");
        check(outerError<.00001,"HUD leaves pixels outside its bounded aperture unchanged");
        check(maxDifference(base,empty)<.00001,"zero energy removes the whole HUD aperture including its edge");
        float[] moving=render(base,live,depths,view,center,0,0,.8f,true,28);
        check(maxDifference(full,moving)>.01,"HUD boundary moves independently of frozen world time");
        write(output.resolve("hud-full.png"),full);write(output.resolve("hud-quarter.png"),quarter);write(output.resolve("hud-empty.png"),empty);
        float[] absent=render(base,live,depths,view,center,0,0,0,1,28,0,0,0);
        float[] half=render(base,live,depths,view,center,0,0,0,1,28,.5f,0,0);
        check(maxDifference(base,absent)<.00001,"entry holds the HUD invisible until its delayed fade starts");
        check(blendError(base,full,half)<.00001,"HUD fade is continuous over the existing scene");
        float[] quiet=render(base,live,depths,view,center,0,0,0,1,28,1,0,0);
        float[] strong=render(base,live,depths,view,center,0,0,0,1,28,1,1,0);
        check(hudOutlineRange(base,strong)>hudOutlineRange(base,quiet)*1.7,"linear-zone HUD has a larger boundary amplitude");
        float[] opening=render(base,live,depths,view,center,0,0,0,2,28,1,0,0);
        float[] spreading=render(base,live,depths,view,center,0,0,0,2,width*.45f,1,0,.5f);
        float[] restored=render(base,live,depths,view,center,0,0,0,2,width*2f,1,0,1);
        check(maxDifference(full,opening)<.00001,"exit begins at the same HUD boundary without replacing the entire screen");
        check(rectDifference(live,spreading,35,height-83,47,height-71)<.00001
                &&rectDifference(base,spreading,width-24,0,width,24)<.00001,"exit reveals reality near the upper-left opening before the far corner");
        check(maxDifference(live,restored)<.00001,"exit completes with unfiltered reality and no residual dark rim");
        float[] zeroStart=render(base,live,depths,view,center,0,0,0,2,0,1,1,0);
        float[] zeroSpreading=render(base,live,depths,view,center,0,0,0,2,width*.4f,1,1,.4f);
        check(maxDifference(base,zeroStart)<.00001&&rectDifference(live,zeroSpreading,35,height-83,47,height-71)<.00001,
                "zero-energy exit starts from a point and still reveals reality outward");
        write(output.resolve("hud-far.png"),strong);write(output.resolve("exit-mid.png"),spreading);write(output.resolve("exit-done.png"),restored);
    }

    private static double hudOutlineRange(float[] base,float[] pixels) {
        double min=Double.POSITIVE_INFINITY,max=0;
        for(int a=0;a<96;a++) {
            double angle=a*Math.PI*2/96,edge=0;
            for(double r=18;r<35;r+=.2) {
                int x=(int)(41+Math.cos(angle)*r),y=(int)(height-77+Math.sin(angle)*r),i=(y*width+x)*4;
                if(Math.abs(base[i]-pixels[i])+Math.abs(base[i+1]-pixels[i+1])+Math.abs(base[i+2]-pixels[i+2])>.03)edge=r;
            }
            min=Math.min(min,edge);max=Math.max(max,edge);
        }
        return max-min;
    }

    private static Matrix4f projection(float yaw, float pitch) {
        return new Matrix4f().perspective((float) Math.toRadians(FOV), (float) width / height, .05f, 1024)
                .rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));
    }

    private static float[] plane(Matrix4f matrix, float distance) {
        Matrix4f inverse = matrix.invert(new Matrix4f());
        float[] result = new float[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            Vector4f p = inverse.transform(new Vector4f((x + .5f) / width * 2 - 1, (y + .5f) / height * 2 - 1, 1, 1));
            p = matrix.transform(new Vector4f(new Vector3f(p.x, p.y, p.z).normalize().mul(distance), 1));
            result[y * width + x] = p.z / p.w * .5f + .5f;
        }
        return result;
    }

    private static float[] scene(boolean color) {
        float[] result = new float[width * height * 4];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = (y * width + x) * 4;
            float u = (x + .5f) / width, v = (y + .5f) / height;
            float grid = ((x * 24 / width + y * 16 / height) & 1) == 0 ? .025f : 0;
            result[i] = color ? .15f + .65f * u + grid : .32f + .13f * v + grid;
            result[i + 1] = color ? .22f + .65f * v + grid : result[i];
            result[i + 2] = color ? .14f + .08f * u : result[i];
            result[i + 3] = 1;
        }
        return result;
    }

    private static float[] solid(float r, float g, float b) {
        float[] result = new float[width * height * 4];
        for (int i = 0; i < result.length; i += 4) { result[i] = r; result[i + 1] = g; result[i + 2] = b; result[i + 3] = 1; }
        return result;
    }

    private static double projectedRadius(float distance) {
        return height / (2 * Math.tan(Math.toRadians(FOV / 2)) * Math.sqrt(distance * distance - 1));
    }

    private static double[] outline(float[] base,float[] pixels,double radius) {
        double[] result=new double[96];
        for(int a=0;a<result.length;a++) {
            double angle=a*Math.PI*2/result.length;
            for(double r=radius*.6;r<radius*1.2;r+=.25) {
                int x=(int)(width*.5+Math.cos(angle)*r),y=(int)(height*.5+Math.sin(angle)*r);
                int i=(y*width+x)*4;
                if(Math.abs(base[i]-pixels[i])+Math.abs(base[i+1]-pixels[i+1])+Math.abs(base[i+2]-pixels[i+2])>.03)result[a]=r;
            }
        }
        return result;
    }
    private static double meanDifference(double[] a,double[] b) {
        double sum=0;for(int i=0;i<a.length;i++)sum+=Math.abs(a[i]-b[i]);return sum/a.length;
    }

    private static int texture(int internal, int format) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, internal, width, height, 0, format, GL_FLOAT, 0L);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return id;
    }

    private static void upload(int texture, int format, float[] pixels) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, format, GL_FLOAT, pixels);
    }

    private static int program(String source) {
        int vertex = shader(GL_VERTEX_SHADER, VERTEX), fragment = shader(GL_FRAGMENT_SHADER, source);
        int result = glCreateProgram();
        glAttachShader(result, vertex);
        glAttachShader(result, fragment);
        glLinkProgram(result);
        if (glGetProgrami(result, GL_LINK_STATUS) == GL_FALSE) throw new AssertionError(glGetProgramInfoLog(result));
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        return result;
    }

    private static int shader(int type, String source) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) throw new AssertionError(glGetShaderInfoLog(id));
        return id;
    }

    private void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label);
        checks++;
        report.append("PASS ").append(label).append('\n');
    }

    private static double maxDifference(float[] a, float[] b) {
        double max = 0;
        for (int i = 0; i < a.length; i++) max = Math.max(max, Math.abs(a[i] - b[i]));
        return max;
    }

    private static double diskDifference(float[] a, float[] b, double radius) {
        double max = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            if (Math.hypot(x + .5 - width * .5, y + .5 - height * .5) < radius)
                for (int c = 0; c < 4; c++) max = Math.max(max, Math.abs(a[(y * width + x) * 4 + c] - b[(y * width + x) * 4 + c]));
        return max;
    }

    private static double outerDifference(float[] a, float[] b, double radius) {
        double max = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            if (Math.hypot(x + .5 - width * .5, y + .5 - height * .5) > radius)
                for (int c = 0; c < 4; c++) max = Math.max(max, Math.abs(a[(y * width + x) * 4 + c] - b[(y * width + x) * 4 + c]));
        return max;
    }

    private static double rectDifference(float[] a, float[] b, int x0, int y0, int x1, int y1) {
        double max = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) for (int c = 0; c < 4; c++)
            max = Math.max(max, Math.abs(a[(y * width + x) * 4 + c] - b[(y * width + x) * 4 + c]));
        return max;
    }

    private static double blendError(float[] base, float[] full, float[] half) {
        double max = 0;
        for (int i = 0; i < base.length; i++) max = Math.max(max, Math.abs((base[i] + full[i]) * .5f - half[i]));
        return max;
    }

    private static double annulusMean(float[] pixels, double inner, double outer, int channel) {
        double sum = 0;
        int count = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            double radius = Math.hypot(x + .5 - width * .5, y + .5 - height * .5);
            if (radius > inner && radius < outer) { sum += pixels[(y * width + x) * 4 + channel]; count++; }
        }
        return sum / count;
    }

    private static double ringFraction(float[] pixels, double radius) {
        int silver = 0, count = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (Math.hypot(x + .5 - width * .5, y + .5 - height * .5) >= radius * .97) continue;
            int i = (y * width + x) * 4;
            if (pixels[i + 2] > .25 && Math.abs(pixels[i] - pixels[i + 2]) < .2) silver++;
            count++;
        }
        return (double) silver / count;
    }

    private static boolean allFiniteOpaque(float[] pixels) {
        for (int i = 0; i < pixels.length; i++)
            if (!Float.isFinite(pixels[i]) || pixels[i] < 0 || pixels[i] > 1 || (i % 4 == 3 && pixels[i] != 1)) return false;
        return true;
    }

    private static void write(Path path, float[] pixels) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = (y * width + x) * 4;
            int r = Math.round(Math.clamp(pixels[i], 0, 1) * 255);
            int g = Math.round(Math.clamp(pixels[i + 1], 0, 1) * 255);
            int b = Math.round(Math.clamp(pixels[i + 2], 0, 1) * 255);
            image.setRGB(x, height - 1 - y, (r << 16) | (g << 8) | b);
        }
        ImageIO.write(image, "png", path.toFile());
    }
}
