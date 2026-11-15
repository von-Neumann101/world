package dev.stillworld.client;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Exercises the shipped aperture fragment shader in an invisible context. */
public final class BladeShaderProbe {
    private static final int SIZE=32;
    private static int checks;
    public static void main(String[] args)throws Exception {
        String fragment=Files.readString(Path.of(args[0],"reality_blade.fsh"));
        try(var errors=GLFWErrorCallback.createPrint(System.err)) {
            errors.set();
            if(!glfwInit())throw new AssertionError("GLFW init");
            glfwDefaultWindowHints();glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED,GLFW_FALSE);glfwWindowHint(GLFW_FOCUS_ON_SHOW,GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
            glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
            long window=glfwCreateWindow(SIZE,SIZE,"Invisible blade probe",0,0);
            if(window==0)throw new AssertionError("GL context");
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
                int frag=compile(GL_FRAGMENT_SHADER,fragment),program=glCreateProgram();
                glAttachShader(program,vertex);glAttachShader(program,frag);glLinkProgram(program);
                check(glGetProgrami(program,GL_LINK_STATUS)==GL_TRUE,glGetProgramInfoLog(program));
                glUseProgram(program);glBindVertexArray(glGenVertexArrays());
                int mask=texture(),scene=texture();
                glUniform1i(glGetUniformLocation(program,"Sampler0"),0);
                glUniform1i(glGetUniformLocation(program,"Sampler1"),1);
                glUniform2f(glGetUniformLocation(program,"ViewportSize"),SIZE,SIZE);
                glUniform4f(glGetUniformLocation(program,"ColorModulator"),1,1,1,1);
                glViewport(0,0,SIZE,SIZE);glDisable(GL_BLEND);
                upload(0,mask,1,1,new float[]{1,1,1,1});
                upload(1,scene,1,1,new float[]{.1f,.7f,.2f,1});
                float[] first=draw();near(first,.1f,.7f,.2f,"live original colors");
                upload(1,scene,1,1,new float[]{.8f,.1f,.3f,1});
                near(draw(),.8f,.1f,.3f,"live scene updates without changing mask");
                upload(0,mask,1,1,new float[]{1,1,1,0});
                near(draw(),.04f,.05f,.06f,"outside aperture preserves scene");
                upload(0,mask,1,1,new float[]{1,1,1,.05f});
                near(draw(),.04f,.05f,.06f,"transparent fringe discarded");
                upload(0,mask,1,1,new float[]{1,1,1,1});
                upload(1,scene,2,1,new float[]{1,0,0,1,0,0,1,1});
                draw();
                near(pixel(4,16),1,0,0,"left screen position samples reality left");
                near(pixel(28,16),0,0,1,"right screen position samples reality right");
                glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL);glClearDepth(.25);
                glClear(GL_DEPTH_BUFFER_BIT);
                near(draw(),.04f,.05f,.06f,"foreground depth occludes aperture");
                glClearDepth(1);glClear(GL_DEPTH_BUFFER_BIT);
                near(draw(),0,0,1,"aperture visible before distant depth");
                glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
                glUniform4f(glGetUniformLocation(program,"ColorModulator"),.5f,.5f,.5f,.3f);
                near(draw(),.028f,.035f,.192f,"colored wearer aperture retains tint and translucency");
                check(glGetError()==GL_NO_ERROR,"no GL errors");
                glDeleteTextures(mask);glDeleteTextures(scene);glDeleteProgram(program);
                glDeleteShader(vertex);glDeleteShader(frag);
                System.out.println("BLADE SHADER PASS: "+checks+" checks; hidden and unfocused");
            }finally{glfwDestroyWindow(window);glfwTerminate();glfwSetErrorCallback(null);}
        }
    }
    private static int texture(){int id=glGenTextures();glBindTexture(GL_TEXTURE_2D,id);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return id;}
    private static void upload(int slot,int texture,int width,int height,float[] data){glActiveTexture(GL_TEXTURE0+slot);glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,width,height,0,GL_RGBA,GL_FLOAT,data);}
    private static float[] draw(){glClearColor(.04f,.05f,.06f,1);glClear(GL_COLOR_BUFFER_BIT);glDrawArrays(GL_TRIANGLES,0,3);return pixel(16,16);}
    private static float[] pixel(int x,int y){float[] p=new float[4];glReadPixels(x,y,1,1,GL_RGBA,GL_FLOAT,p);return p;}
    private static void near(float[] p,float r,float g,float b,String label){check(Math.abs(p[0]-r)<.015&&Math.abs(p[1]-g)<.015&&Math.abs(p[2]-b)<.015,label+" got "+java.util.Arrays.toString(p));}
    private static int compile(int type,String code){int shader=glCreateShader(type);glShaderSource(shader,code);glCompileShader(shader);check(glGetShaderi(shader,GL_COMPILE_STATUS)==GL_TRUE,glGetShaderInfoLog(shader));return shader;}
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
}
