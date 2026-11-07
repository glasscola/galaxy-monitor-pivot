package com.pivotcontrol.app;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import java.nio.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** One GPU texture, shared by every crop; no bitmap readback, files, or codec. */
final class StreamRenderer extends GLSurfaceView implements GLSurfaceView.Renderer {
    interface Ready {void ready(Surface surface);void error(String reason);}
    private final Ready listener;private final Handler main=new Handler(Looper.getMainLooper());
    private SurfaceTexture input;private Surface surface;private int program,tex,position,uv,matrixLoc,stepLoc,sharpLoc,boundsLoc;
    private int width,height,sourceW,sourceH,framebuffer,compositeTexture,copyProgram,copyPosition,copyUv;private boolean hasComposite;private final float[] transform=new float[16];
    private final FloatBuffer vertices=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer(),coords=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private volatile Scene scene=new Scene();private volatile boolean paused,closed;private boolean fresh,hasFrame;private long lastFrame;
    StreamRenderer(Context c,int w,int h,Ready r){super(c);sourceW=w;sourceH=h;listener=r;setEGLContextClientVersion(2);setPreserveEGLContextOnPause(true);setRenderer(this);setRenderMode(RENDERMODE_WHEN_DIRTY);}
    void configure(Scene s){scene=s.copy();requestRender();}
    void pauseStream(boolean pause){paused=pause;requestRender();}
    void resizeSource(int w,int h){queueEvent(()->{sourceW=w;sourceH=h;if(input!=null)input.setDefaultBufferSize(w,h);});}
    @Override public void onSurfaceCreated(GL10 unused,EGLConfig config){try{
        int[] names=new int[1];GLES20.glGenTextures(1,names,0);tex=names[0];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,tex);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        // Keep texture coordinates precise at native phone resolutions. ES 2.0
        // permits drivers without fragment highp, so query before using it.
        int[] range=new int[2],precision=new int[1];GLES20.glGetShaderPrecisionFormat(GLES20.GL_FRAGMENT_SHADER,GLES20.GL_HIGH_FLOAT,range,0,precision,0);
        String fp=precision[0]>0?"highp":"mediump",varying="varying "+fp+" vec2 v;";
        String vs="attribute vec2 p;attribute vec2 uv;uniform mat4 m;"+varying+"void main(){gl_Position=vec4(p,0.,1.);v=(m*vec4(uv,0.,1.)).xy;}";
        String fs="#extension GL_OES_EGL_image_external : require\nprecision "+fp+" float;uniform samplerExternalOES image;uniform vec2 stepSize;uniform vec4 bounds;uniform float sharp;"+varying+"vec3 readAt(vec2 q){return texture2D(image,clamp(q,bounds.xy,bounds.zw)).rgb;}void main(){vec3 c=readAt(v);if(sharp>0.){vec3 n=(readAt(v+vec2(stepSize.x,0.))+readAt(v-vec2(stepSize.x,0.))+readAt(v+vec2(0.,stepSize.y))+readAt(v-vec2(0.,stepSize.y)))*.25;c=clamp(c+(c-n)*sharp,0.,1.);}gl_FragColor=vec4(c,1.);}";
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,shader(GLES20.GL_VERTEX_SHADER,vs));GLES20.glAttachShader(program,shader(GLES20.GL_FRAGMENT_SHADER,fs));GLES20.glLinkProgram(program);int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        position=GLES20.glGetAttribLocation(program,"p");uv=GLES20.glGetAttribLocation(program,"uv");matrixLoc=GLES20.glGetUniformLocation(program,"m");stepLoc=GLES20.glGetUniformLocation(program,"stepSize");sharpLoc=GLES20.glGetUniformLocation(program,"sharp");boundsLoc=GLES20.glGetUniformLocation(program,"bounds");
        copyProgram=GLES20.glCreateProgram();GLES20.glAttachShader(copyProgram,shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p;attribute vec2 uv;"+varying+"void main(){gl_Position=vec4(p,0.,1.);v=uv;}"));GLES20.glAttachShader(copyProgram,shader(GLES20.GL_FRAGMENT_SHADER,"precision "+fp+" float;uniform sampler2D image;"+varying+"void main(){gl_FragColor=texture2D(image,v);}"));GLES20.glLinkProgram(copyProgram);GLES20.glGetProgramiv(copyProgram,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(copyProgram));copyPosition=GLES20.glGetAttribLocation(copyProgram,"p");copyUv=GLES20.glGetAttribLocation(copyProgram,"uv");
        input=new SurfaceTexture(tex);input.setDefaultBufferSize(sourceW,sourceH);input.setOnFrameAvailableListener(st->{synchronized(this){fresh=true;}requestRender();});surface=new Surface(input);main.post(()->{if(!closed)listener.ready(surface);});
    }catch(Exception e){main.post(()->listener.error(e.toString()));}}
    private int shader(int type,String code){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,code);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
    @Override public void onSurfaceChanged(GL10 g,int w,int h){width=w;height=h;GLES20.glViewport(0,0,w,h);if(framebuffer!=0)GLES20.glDeleteFramebuffers(1,new int[]{framebuffer},0);if(compositeTexture!=0)GLES20.glDeleteTextures(1,new int[]{compositeTexture},0);int[] id=new int[1];GLES20.glGenTextures(1,id,0);compositeTexture=id[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,compositeTexture);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,w,h,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);GLES20.glGenFramebuffers(1,id,0);framebuffer=id[0];GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer);GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,compositeTexture,0);if(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)!=GLES20.GL_FRAMEBUFFER_COMPLETE)main.post(()->listener.error("외부 화면 버퍼를 만들 수 없습니다."));GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);hasComposite=false;}
    @Override public void onDrawFrame(GL10 unused){
        if(closed||input==null||width==0)return;Scene s=scene;long now=System.nanoTime();
        synchronized(this){if(fresh){long wait=1_000_000_000L/s.fps-(now-lastFrame);if(wait<=0||!hasFrame){input.updateTexImage();input.getTransformMatrix(transform);fresh=false;hasFrame=true;lastFrame=now;}else main.postDelayed(this::requestRender,Math.max(1,wait/1_000_000));}}
        // Drain the input even while editing, but retain the last clean composite on GPU.
        if(paused&&hasComposite){present();return;}
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer);
        GLES20.glClearColor(.015f,.02f,.035f,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);if(!hasFrame){present();return;}
        GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,tex);GLES20.glUniformMatrix4fv(matrixLoc,1,false,transform,0);GLES20.glUniform2f(stepLoc,1f/sourceW,1f/sourceH);GLES20.glUniform1f(sharpLoc,s.sharpness);
        int[] canvas=s.canvas(width,height);int angle=MirrorGeometry.angle(width,height,s.portrait,s.reverse);
        if(!s.regions)draw(new Scene.Box(0,0,1,1),new Scene.Box(0,0,1,1),s,canvas,angle);
        else for(Scene.Region region:s.items)draw(region.source,region.dest,s,canvas,angle);
        hasComposite=true;present();
    }
    private void present(){GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);GLES20.glUseProgram(copyProgram);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,compositeTexture);vertices.clear();vertices.put(new float[]{-1,1,1,1,-1,-1,1,-1}).position(0);coords.clear();coords.put(new float[]{0,1,1,1,0,0,1,0}).position(0);GLES20.glEnableVertexAttribArray(copyPosition);GLES20.glVertexAttribPointer(copyPosition,2,GLES20.GL_FLOAT,false,0,vertices);GLES20.glEnableVertexAttribArray(copyUv);GLES20.glVertexAttribPointer(copyUv,2,GLES20.GL_FLOAT,false,0,coords);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);}
    private void draw(Scene.Box source,Scene.Box dest,Scene s,int[] size,int angle){
        float[] a=Scene.placement(source,dest,sourceW,sourceH,size[0],size[1],s.scaling);float x=a[4],y=a[5],r=x+a[6],b=y+a[7];
        float[] xy={x,y,r,y,x,b,r,b};double rad=Math.toRadians(angle);float cos=(float)Math.cos(rad),sin=(float)Math.sin(rad);
        for(int i=0;i<8;i+=2){float xx=xy[i]-size[0]/2f,yy=xy[i+1]-size[1]/2f;float ox=xx*cos-yy*sin+width/2f,oy=xx*sin+yy*cos+height/2f;xy[i]=2*ox/width-1;xy[i+1]=1-2*oy/height;}
        vertices.clear();vertices.put(xy).position(0);coords.clear();coords.put(new float[]{a[0],1-a[1],a[2],1-a[1],a[0],1-a[3],a[2],1-a[3]}).position(0);
        // Clamp filtering to source interior, excluding the phone's outside-only guide pixels.
        float x1=transform[0]*a[0]+transform[4]*(1-a[1])+transform[12],y1=transform[1]*a[0]+transform[5]*(1-a[1])+transform[13];
        float x2=transform[0]*a[2]+transform[4]*(1-a[3])+transform[12],y2=transform[1]*a[2]+transform[5]*(1-a[3])+transform[13];
        GLES20.glUniform4f(boundsLoc,Math.min(x1,x2)+.5f/sourceW,Math.min(y1,y2)+.5f/sourceH,Math.max(x1,x2)-.5f/sourceW,Math.max(y1,y2)-.5f/sourceH);
        GLES20.glEnableVertexAttribArray(position);GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,0,vertices);GLES20.glEnableVertexAttribArray(uv);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,0,coords);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
    }
    void close(){closed=true;main.removeCallbacksAndMessages(null);queueEvent(()->{if(surface!=null)surface.release();if(input!=null){input.setOnFrameAvailableListener(null);input.release();}GLES20.glDeleteProgram(program);GLES20.glDeleteProgram(copyProgram);GLES20.glDeleteFramebuffers(1,new int[]{framebuffer},0);GLES20.glDeleteTextures(2,new int[]{tex,compositeTexture},0);});}
}
