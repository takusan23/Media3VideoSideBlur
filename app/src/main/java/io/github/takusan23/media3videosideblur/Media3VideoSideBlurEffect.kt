package io.github.takusan23.media3videosideblur

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** ExoPlayer で両端をぼかすやつ */
@UnstableApi
class Media3VideoSideBlurEffect : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return BlurEffectGlShaderProgram(useHdr, 1)
    }

    private class BlurEffectGlShaderProgram(
        useHighPrecisionColorComponents: Boolean,
        texturePoolCapacity: Int
    ) : BaseGlShaderProgram(useHighPrecisionColorComponents, texturePoolCapacity) {

        private val glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        private val identityTransformationMatrix = GlUtil.create4x4IdentityMatrix()
        private val identityTexTransformationMatrix = GlUtil.create4x4IdentityMatrix()

        private var size: Size? = null

        init {
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            // Uniform 変数を更新
            glProgram.setFloatsUniform("uTransformationMatrix", identityTransformationMatrix)
            glProgram.setFloatsUniform("uTexTransformationMatrix", identityTexTransformationMatrix)

            // アルファブレンディングを有効
            // 2回 glDrawArrays してブラーの背景の上に動画を重ねるため
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            val size = Size(inputWidth, inputHeight)
            // Uniform 変数でも使いたい
            this.size = size
            return size
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            glProgram.use()
            // テクスチャID（映像フレーム）をセット
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            // サイズを Uniform 変数に入れる
            glProgram.setFloatsUniform("vResolution", floatArrayOf(size!!.width.toFloat(), size!!.height.toFloat()))

            // 描画する。まず背景（ブラー）
            glProgram.setIntUniform("iDrawMode", 1)
            // 3倍くらいに拡大してはみ出させる
            Matrix.setIdentityM(identityTransformationMatrix, 0)
            Matrix.scaleM(identityTransformationMatrix, 0, 3.5f, 3.5f, 1f)
            glProgram.setFloatsUniform("uTransformationMatrix", identityTransformationMatrix)
            glProgram.bindAttributesAndUniforms()
            // The four-vertex triangle strip forms a quad.
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)

            // 次に最前面の動画を
            glProgram.setIntUniform("iDrawMode", 2)
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            // はみ出しは戻す
            Matrix.setIdentityM(identityTransformationMatrix, 0)
            glProgram.setFloatsUniform("uTransformationMatrix", identityTransformationMatrix)
            glProgram.bindAttributesAndUniforms()
            // The four-vertex triangle strip forms a quad.
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
        }

        companion object {

            /** バーテックスシェーダー */
            private const val VERTEX_SHADER = """#version 300 es
                in vec4 aFramePosition;
                uniform mat4 uTransformationMatrix;
                uniform mat4 uTexTransformationMatrix;
                
                out vec2 vTexSamplingCoord;
                
                void main() {
                  gl_Position = uTransformationMatrix * aFramePosition;
                  vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5,
                                              aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
                  vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
                }    
            """

            /**
             * フラグメントシェーダー
             * thx!!!!
             * https://github.com/GameMakerDiscord/blur-shaders
             */
            private const val FRAGMENT_SHADER = """#version 300 es
                precision highp float;
                
                in vec2 vTexSamplingCoord;
                uniform sampler2D uTexSampler;
                
                // どっちを描画するか。1 = 背景(ブラー) / 2 = 最前面(ブラーしない)
                uniform int iDrawMode;
                
                // 動画のサイズ
                uniform vec2 vResolution;
                
                // ぼかし
                const int Quality = 3;
                const int Directions = 16;
                const float Pi = 6.28318530718; //pi * 2
                const float Radius = 16.0; // ぼかし具合
                
                // 出力する色
                out vec4 fragColor;
                
                void main()
                {
                    vec2 radius = Radius / vResolution.xy;
                    vec4 Color = texture( uTexSampler, vTexSamplingCoord);
                    
                    // 背景を描画するモード
                    if (iDrawMode == 1) {
                        for( float d=0.0;d<Pi;d+=Pi/float(Directions) )
                        {
                            for( float i=1.0/float(Quality);i<=1.0;i+=1.0/float(Quality) )
                            {
                                Color += texture( uTexSampler, vTexSamplingCoord+vec2(cos(d),sin(d))*radius*i);
                            }
                        }
                        Color /= float(Quality)*float(Directions)+1.0;
                        fragColor = Color;
                    }
                    
                    // 最前面の動画を描画するモード                    
                    if (iDrawMode == 2) {
                        fragColor = Color;
                    }
                }
"""
        }
    }
}