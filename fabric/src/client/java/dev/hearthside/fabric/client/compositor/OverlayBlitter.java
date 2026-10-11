package dev.hearthside.fabric.client.compositor;

import static org.lwjgl.opengl.GL45C.*;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import dev.hearthside.fabric.HearthsideMod;

/**
 * Draws a texture the size of the window over the window, blended as premultiplied alpha (Minecraft's GUI blending
 * onto a transparent target leaves exactly that). A single full-window triangle and {@code texelFetch}, so no
 * filtering or sampler state is involved. State Minecraft tracks goes through {@link GlStateManager}, so its cache
 * stays true. Render thread.
 */
final class OverlayBlitter {
	private static final String VERTEX = """
			#version 330 core
			void main() {
				vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
				gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
			}
			""";
	private static final String FRAGMENT = """
			#version 330 core
			uniform sampler2D overlay;
			out vec4 color;
			void main() {
				color = texelFetch(overlay, ivec2(gl_FragCoord.xy), 0);
			}
			""";

	private int program;
	private int vao;
	private boolean broken;

	void draw(int texture, int width, int height) {
		if (broken || (program == 0 && !build())) {
			return;
		}
		GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
		GlStateManager._viewport(0, 0, width, height);
		GlStateManager._disableScissorTest();
		GlStateManager._disableDepthTest();
		GlStateManager._disableCull();
		GlStateManager._colorMask(15);
		GlStateManager._enableBlend(0);
		GlStateManager._blendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager._glUseProgram(program);
		GlStateManager._glBindVertexArray(vao);
		GlStateManager._activeTexture(GL_TEXTURE0);
		GlStateManager._bindTexture(texture);
		glDrawArrays(GL_TRIANGLES, 0, 3);
		GlStateManager._glBindVertexArray(0);
		GlStateManager._glUseProgram(0);
		GlStateManager._disableBlend(0);
	}

	void close() {
		if (program != 0) {
			glDeleteProgram(program);
			glDeleteVertexArrays(vao);
			program = 0;
		}
	}

	private boolean build() {
		int vertex = compile(GL_VERTEX_SHADER, VERTEX);
		int fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT);
		if (vertex == 0 || fragment == 0) {
			broken = true;
			return false;
		}
		int linked = glCreateProgram();
		glAttachShader(linked, vertex);
		glAttachShader(linked, fragment);
		glLinkProgram(linked);
		glDeleteShader(vertex);
		glDeleteShader(fragment);
		if (glGetProgrami(linked, GL_LINK_STATUS) == GL_FALSE) {
			HearthsideMod.LOGGER.error("Hearthside overlay shader did not link: {}", glGetProgramInfoLog(linked));
			glDeleteProgram(linked);
			broken = true;
			return false;
		}
		program = linked;
		vao = glCreateVertexArrays();
		return true;
	}

	private static int compile(int type, String source) {
		int shader = glCreateShader(type);
		glShaderSource(shader, source);
		glCompileShader(shader);
		if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
			HearthsideMod.LOGGER.error("Hearthside overlay shader did not compile: {}", glGetShaderInfoLog(shader));
			glDeleteShader(shader);
			return 0;
		}
		return shader;
	}
}
