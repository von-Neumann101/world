package dev.stillworld.client;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Pixel acceptance for the actual shipped rift material; no visible window or mouse capture. */
public final class RiftShaderProbe {
    private static final int WIDTH=512,HEIGHT=384;
    private static int checks;
    public static void main(String[] args)throws Exception {
        try(var errors=GLFWErrorCallback.createPrint(System.err)) {
            errors.set();check(glfwInit(),"GLFW init");
            glfwDefaultWindowHints();glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED,GLFW_FALSE);glfwWindowHint(GLFW_FOCUS_ON_SHOW,GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
            glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
            long window=glfwCreateWindow(WIDTH,HEIGHT,"Invisible rift probe",0,0);
            check(window!=0,"GL context");
            try {
                glfwMakeContextCurrent(window);GL.createCapabilities();
                check(glfwGetInputMode(window,GLFW_CURSOR)==GLFW_CURSOR_NORMAL,"ordinary mouse");
                check(glfwGetWindowAttrib(window,GLFW_VISIBLE)==GLFW_FALSE,"hidden window");
                int vertex=compile(GL_VERTEX_SHADER,"""
                    #version 150
                    out vec2 texCoord0;
                    out vec4 vertexColor;
                    void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);texCoord0=p;
                    vertexColor=vec4(1);gl_Position=vec4(p*2-1,0,1);}
                    """);
                int fragment=compile(GL_FRAGMENT_SHADER,Files.readString(Path.of(args[0]))),program=glCreateProgram();
                glAttachShader(program,vertex);glAttachShader(program,fragment);glLinkProgram(program);
                check(glGetProgrami(program,GL_LINK_STATUS)==GL_TRUE,glGetProgramInfoLog(program));
                glUseProgram(program);int vao=glGenVertexArrays();glBindVertexArray(vao);
                glUniform1i(glGetUniformLocation(program,"Sampler0"),0);
                glUniform1i(glGetUniformLocation(program,"Sampler1"),1);
                glUniform2f(glGetUniformLocation(program,"ViewportSize"),WIDTH,HEIGHT);
                glUniform4f(glGetUniformLocation(program,"ColorModulator"),1,1,1,1);
                int mask=texture(0),scene=texture(1);
                upload(0,mask,1,1,new float[]{1,1,1,1});
                upload(1,scene,1,1,new float[]{.1f,.7f,.2f,1});
                glViewport(0,0,WIDTH,HEIGHT);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
                float[] pixels=draw();
                near(pixels,256,192,.1f,.7f,.2f,"opening shows unfiltered reality");
                near(pixels,64,320,.2f,.2f,.2f,"old rectangle is fully absent outside the cut");
                near(pixels,448,64,.2f,.2f,.2f,"opposite corner is transparent");
                int middle=columnSize(pixels,256),left=columnSize(pixels,12),right=columnSize(pixels,499);
                check(middle>=26&&middle<=36,"slender opening, about forty percent of the original width");
                check(left>0&&right>0&&left<middle*.2&&right<middle*.2,"both ends taper to fine tips");
                check(columnSize(pixels,0)<4&&columnSize(pixels,511)<4,"tips close at the ends");
                write(Path.of(args[1],"rift-mask.png"),pixels);
                upload(1,scene,1,1,new float[]{.8f,.1f,.3f,1});
                near(draw(),256,192,.8f,.1f,.3f,"new live frame is visible without changing the rift");
                upload(1,scene,2,1,new float[]{1,0,0,1,0,0,1,1});
                pixels=draw();
                check(coloredInColumn(pixels,170,0)>8,"left of slit samples left screen reality");
                check(coloredInColumn(pixels,342,2)>8,"right of slit samples right screen reality");
                glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL);glClearDepth(.25);glClear(GL_DEPTH_BUFFER_BIT);
                near(draw(),256,192,.2f,.2f,.2f,"foreground terrain occludes the opening");
                glClearDepth(1);glClear(GL_DEPTH_BUFFER_BIT);
                near(draw(),256,192,0,0,1,"opening remains visible in front of distant terrain");
                check(glGetError()==GL_NO_ERROR,"no GL errors");
                glDeleteTextures(mask);glDeleteTextures(scene);glDeleteVertexArrays(vao);
                glDeleteProgram(program);glDeleteShader(vertex);glDeleteShader(fragment);
                System.out.println("RIFT SHADER PASS: "+checks+" checks; hidden and unfocused");
            }finally{glfwDestroyWindow(window);glfwTerminate();glfwSetErrorCallback(null);}
        }
    }
    private static int texture(int slot){glActiveTexture(GL_TEXTURE0+slot);int id=glGenTextures();glBindTexture(GL_TEXTURE_2D,id);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return id;}
    private static void upload(int slot,int texture,int w,int h,float[] data){glActiveTexture(GL_TEXTURE0+slot);glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,w,h,0,GL_RGBA,GL_FLOAT,data);}
    private static float[] draw(){glClearColor(.2f,.2f,.2f,1);glClear(GL_COLOR_BUFFER_BIT);glDrawArrays(GL_TRIANGLES,0,3);float[] p=new float[WIDTH*HEIGHT*4];glReadPixels(0,0,WIDTH,HEIGHT,GL_RGBA,GL_FLOAT,p);return p;}
    private static int columnSize(float[] p,int x){int n=0;for(int y=0;y<HEIGHT;y++){int i=(y*WIDTH+x)*4;if(Math.abs(p[i]-.2)+Math.abs(p[i+1]-.2)+Math.abs(p[i+2]-.2)>.07)n++;}return n;}
    private static int coloredInColumn(float[] p,int x,int channel){int n=0;for(int y=0;y<HEIGHT;y++){int i=(y*WIDTH+x)*4;if(p[i+channel]>.9&&p[i+(channel+1)%3]<.1)n++;}return n;}
    private static void near(float[] p,int x,int y,float r,float g,float b,String label){int i=(y*WIDTH+x)*4;check(Math.abs(p[i]-r)<.015&&Math.abs(p[i+1]-g)<.015&&Math.abs(p[i+2]-b)<.015,label);}
    private static void write(Path path,float[] p)throws Exception{Files.createDirectories(path.getParent());var image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int i=(y*WIDTH+x)*4;image.setRGB(x,HEIGHT-1-y,(Math.round(p[i]*255)<<16)|(Math.round(p[i+1]*255)<<8)|Math.round(p[i+2]*255));}ImageIO.write(image,"png",path.toFile());}
    private static int compile(int type,String source){int shader=glCreateShader(type);glShaderSource(shader,source);glCompileShader(shader);check(glGetShaderi(shader,GL_COMPILE_STATUS)==GL_TRUE,glGetShaderInfoLog(shader));return shader;}
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
}
