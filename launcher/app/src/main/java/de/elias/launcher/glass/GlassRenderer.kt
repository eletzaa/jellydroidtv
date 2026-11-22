package de.elias.launcher.glass

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Zeichnet Hintergrund und Liquid Glass mit OpenGL ES 2.0. Android 11 hat keine eingebauten
 * Glaseffekte, deshalb berechnet dieser Shader die Lichtbrechung selbst (wie im Mockup).
 */
class GlassRenderer(
    private val state: GlassState,
    private val view: GLSurfaceView,
) : GLSurfaceView.Renderer {

    private var program = 0
    private var texture = 0
    private var uploadedVersion = -1
    private var width = 1
    private var height = 1
    private val loc = HashMap<String, Int>()
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }
    private val rects = FloatArray(GlassState.MAX_PANELS * 4)
    private val params = FloatArray(GlassState.MAX_PANELS * 4)

    // Leistungstest: die ersten Bilder werden gemessen, danach wird entschieden.
    private val probeTimes = ArrayList<Long>()
    private var probing = true

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(VERTEX, FRAGMENT)
        GLES20.glUseProgram(program)
        listOf("p").forEach { loc[it] = GLES20.glGetAttribLocation(program, it) }
        listOf("u_tex", "u_res", "u_s", "u_n", "u_rect", "u_prm").forEach { loc[it] = GLES20.glGetUniformLocation(program, it) }
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        uploadedVersion = -1
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        val start = System.nanoTime()
        val bmp = state.backdrop
        if (bmp != null && state.backdropVersion != uploadedVersion && !bmp.isRecycled) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            uploadedVersion = state.backdropVersion
        }
        if (uploadedVersion < 0) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }

        val panels = if (state.mode == GlassMode.Refraction) state.snapshot() else emptyList()
        rects.fill(0f)
        params.fill(0f)
        panels.forEachIndexed { i, p ->
            rects[i * 4] = p.left; rects[i * 4 + 1] = p.top; rects[i * 4 + 2] = p.width; rects[i * 4 + 3] = p.height
            params[i * 4] = minOf(p.width, p.height) / 2f; params[i * 4 + 1] = p.alpha
            params[i * 4 + 2] = p.strength; params[i * 4 + 3] = p.tint
        }

        GLES20.glUseProgram(program)
        val ap = loc.getValue("p")
        GLES20.glEnableVertexAttribArray(ap)
        GLES20.glVertexAttribPointer(ap, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(loc.getValue("u_tex"), 0)
        GLES20.glUniform2f(loc.getValue("u_res"), width.toFloat(), height.toFloat())
        GLES20.glUniform1f(loc.getValue("u_s"), width / 1600f)
        GLES20.glUniform1i(loc.getValue("u_n"), panels.size)
        GLES20.glUniform4fv(loc.getValue("u_rect"), GlassState.MAX_PANELS, rects, 0)
        GLES20.glUniform4fv(loc.getValue("u_prm"), GlassState.MAX_PANELS, params, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        if (probing && panels.isNotEmpty()) {
            GLES20.glFinish()
            probeTimes += System.nanoTime() - start
            if (probeTimes.size >= PROBE_FRAMES) finishProbe() else view.requestRender()
        }
    }

    private fun finishProbe() {
        probing = false
        val sorted = probeTimes.sorted()
        val medianMs = sorted[sorted.size / 2] / 1_000_000.0
        lastProbeMs = medianMs
        if (medianMs > MAX_FRAME_MS) {
            Handler(Looper.getMainLooper()).post { state.mode = GlassMode.Matte }
        }
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "Shader-Fehler: " + GLES20.glGetShaderInfoLog(s) }
            return s
        }
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(prog, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(prog)
        return prog
    }

    companion object {
        private const val PROBE_FRAMES = 40
        /** Mehr als ~16 ms pro Bild heißt: unter 60 Bildern pro Sekunde, dann mattes Glas. */
        private const val MAX_FRAME_MS = 16.0
        @Volatile var lastProbeMs: Double = -1.0

        private const val VERTEX = "attribute vec2 p;void main(){gl_Position=vec4(p,0.,1.);}"

        private val FRAGMENT = """
precision highp float;
uniform sampler2D u_tex;uniform vec2 u_res;uniform float u_s;uniform int u_n;
uniform vec4 u_rect[8];uniform vec4 u_prm[8];
float sdRR(vec2 p,vec2 b,float r){vec2 q=abs(p)-b+r;return length(max(q,0.))+min(max(q.x,q.y),0.)-r;}
vec3 T(vec2 px){return texture2D(u_tex,clamp(px/u_res,vec2(0.001),vec2(0.999))).rgb;}
void main(){
  vec2 px=vec2(gl_FragCoord.x,u_res.y-gl_FragCoord.y);
  vec3 col=T(px);
  for(int i=0;i<8;i++){ if(i>=u_n)break;
    vec4 R=u_rect[i];vec4 P=u_prm[i];
    vec2 b=R.zw*.5;vec2 c=R.xy+b;
    float ds=sdRR(px-c-vec2(0.,8.*u_s),b,P.x);
    float d0=sdRR(px-c,b,P.x);
    if(d0>0.) col*=1.-.2*P.y*(1.-smoothstep(0.,26.*u_s,ds));
  }
  for(int i=0;i<8;i++){ if(i>=u_n)break;
    vec4 R=u_rect[i];vec4 P=u_prm[i];
    vec2 b=R.zw*.5;vec2 c=R.xy+b;vec2 q=px-c;
    float d=sdRR(q,b,P.x);
    if(d<1.){
      float e=1.;
      vec2 n=normalize(vec2(sdRR(q+vec2(e,0.),b,P.x)-sdRR(q-vec2(e,0.),b,P.x),sdRR(q+vec2(0.,e),b,P.x)-sdRR(q-vec2(0.,e),b,P.x))+1e-5);
      float th=min(min(b.x,b.y),24.*u_s);
      float k=clamp(-d/th,0.,1.);
      float bend=pow(1.-k,2.2)*P.z*th*2.6;
      vec2 base=c+q*.9;
      vec2 off=-n*bend;
      vec3 acc=vec3(0.);float fr=1.0*u_s;
      for(int j=0;j<8;j++){float a=float(j)*.785398;acc+=T(base+off+vec2(cos(a),sin(a))*fr);}
      acc=(acc+T(base+off)*2.)/10.;
      float rr=T(base+off*1.18).r;float bb=T(base+off*.82).b;
      vec3 glass=vec3(mix(acc.r,rr,.6),acc.g,mix(acc.b,bb,.6));
      glass=glass*1.04+.008;
      glass=mix(glass,vec3(1.),P.w);
      vec2 L=normalize(vec2(-.55,-.85));
      float lit=dot(n,L);
      float rim=1.-smoothstep(0.,2.2*u_s,-d);
      float hi=rim*(.30+.55*max(lit,0.))+rim*max(-lit,0.)*.18;
      hi+=pow(1.-k,5.)*max(lit,0.)*.34;
      glass+=hi;
      float a=P.y*(1.-smoothstep(-1.,1.,d));
      col=mix(col,glass,a);
    }
  }
  gl_FragColor=vec4(col,1.);
}
""".trimIndent()
    }
}
