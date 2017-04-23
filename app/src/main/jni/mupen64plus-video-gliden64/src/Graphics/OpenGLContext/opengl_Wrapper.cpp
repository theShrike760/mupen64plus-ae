#include "opengl_Wrapper.h"

namespace opengl {

	bool FunctionWrapper::m_threaded_wrapper = false;
	bool FunctionWrapper::m_shutdown = false;
	int FunctionWrapper::m_swapBuffersQueued = 0;
	std::thread FunctionWrapper::m_commandExecutionThread;
	std::mutex FunctionWrapper::m_condvarMutex;
	std::condition_variable FunctionWrapper::m_condition;
	BlockingQueue<std::shared_ptr<OpenGlCommand>> FunctionWrapper::m_commandQueue;

	void FunctionWrapper::executeCommand(std::shared_ptr<OpenGlCommand> _command)
	{
		if (m_threaded_wrapper) {
			m_commandQueue.push(_command);
			_command->waitOnCommand();
		} else {
			_command->performCommandSingleThreaded();
		}
	}

	void FunctionWrapper::executePriorityCommand(std::shared_ptr<OpenGlCommand> _command)
	{
		if (m_threaded_wrapper) {
			m_commandQueue.pushBack(_command);
			_command->waitOnCommand();
		} else {
			_command->performCommandSingleThreaded();
		}
	}

	void FunctionWrapper::commandLoop(void)
	{
		while(!m_shutdown || m_commandQueue.size() != 0)
		{
			std::shared_ptr<OpenGlCommand> command;

			if(m_commandQueue.tryPop(command, std::chrono::milliseconds(10))) {
				command->performCommand();
			}
		}
	}

	void FunctionWrapper::setThreadedMode(void)
	{
		m_threaded_wrapper = true;

		m_commandExecutionThread = std::thread(&FunctionWrapper::commandLoop);
	}

	void FunctionWrapper::glBlendFunc(const GLenum& sfactor, const GLenum& dfactor)
	{
		executeCommand(std::make_shared<GlBlendFuncCommand>(sfactor, dfactor));
	}

	void FunctionWrapper::glPixelStorei(const GLenum& pname, const GLint& param)
	{
		executeCommand(std::make_shared<GlPixelStoreiCommand>(pname, param));
	}

	void FunctionWrapper::glClearColor(const GLfloat& red, const GLfloat& green, const GLfloat& blue, const GLfloat& alpha)
	{
		executeCommand(std::make_shared<GlClearColorCommand>(red, green, blue, alpha));
	}

	void FunctionWrapper::glCullFace(const GLenum& mode)
	{
		executeCommand(std::make_shared<GlCullFaceCommand>(mode));
	}

	void FunctionWrapper::glDepthFunc(const GLenum& func)
	{
		executeCommand(std::make_shared<GlDepthFuncCommand>(func));
	}

	void FunctionWrapper::glDepthMask(const GLboolean& flag)
	{
		executeCommand(std::make_shared<GlDepthMaskCommand>(flag));
	}

	void FunctionWrapper::glDisable(const GLenum& cap)
	{
		executeCommand(std::make_shared<GlDisableCommand>(cap));
	}

	void FunctionWrapper::glEnable(const GLenum& cap)
	{
		executeCommand(std::make_shared<GlEnableCommand>(cap));
	}

	void FunctionWrapper::glPolygonOffset(const GLfloat& factor, const GLfloat& units)
	{
		executeCommand(std::make_shared<GlPolygonOffsetCommand>(factor, units));
	}

	void FunctionWrapper::glScissor(const GLint& x, const GLint& y, const GLsizei& width, const GLsizei& height)
	{
		executeCommand(std::make_shared<GlScissorCommand>(x, y, width, height));
	}

	void FunctionWrapper::glViewport(const GLint& x, const GLint& y, const GLsizei& width, const GLsizei& height)
	{
		executeCommand(std::make_shared<GlViewportCommand>(x, y, width, height));
	}

	void FunctionWrapper::glBindTexture(const GLenum& target, const GLuint& texture)
	{
		executeCommand(std::make_shared<GlBindTextureCommand>(target, texture));
	}

	void FunctionWrapper::glTexParameteri(const GLenum& target, const GLenum& pname, const GLint& param)
	{
		executeCommand(std::make_shared<GlTexParameteriCommand>(target, pname, param));
	}

	void FunctionWrapper::glGetIntegerv(const GLenum& pname, GLint* data)
	{
		executeCommand(std::make_shared<GlGetIntegervCommand>(pname, data));
	}

	const GLubyte* FunctionWrapper::glGetString(const GLenum& name)
	{
		const GLubyte* returnValue;
		executeCommand(std::make_shared<GlGetStringCommand>(name, returnValue));

		return returnValue;
	}

	void FunctionWrapper::glReadPixels(const GLint& x, const GLint& y, const GLsizei& width, const GLsizei& height, const GLenum& format, const GLenum& type, void *pixels)
	{
		executeCommand(std::make_shared<GlReadPixelsCommand>(x, y, width, height, format, type, pixels));
	}

	void FunctionWrapper::glReadPixelsAsync(const GLint& x, const GLint& y, const GLsizei& width, const GLsizei& height, const GLenum& format, const GLenum& type)
	{
		executeCommand(std::make_shared<GlReadPixelsAsyncCommand>(x, y, width, height, format, type));
	}

	void FunctionWrapper::glTexSubImage2DBuffered(const GLenum& target, const GLint& level, const GLint& xoffset, const GLint& yoffset, const GLsizei& width, const GLsizei& height, const GLenum& format, const GLenum& type, std::size_t offset)
	{
		executeCommand(std::make_shared<GlTexSubImage2DBufferedCommand>(target, level, xoffset, yoffset, width, height, format, type, offset));
	}

	void FunctionWrapper::glDrawArrays(const GLenum& mode, const GLint& first, const GLsizei& count)
	{
		executeCommand(std::make_shared<GlDrawArraysCommand>(mode, first, count));
	}
	void FunctionWrapper::glDrawArraysUnbuffered(const GLenum& mode, const GLint& first, const GLsizei& count, std::unique_ptr<std::vector<char>> data)
	{
		executeCommand(std::make_shared<GlDrawArraysUnbufferedCommand>(mode, first, count, std::move(data)));
	}

	GLenum FunctionWrapper::glGetError(void)
	{
#ifdef GL_DEBUG
		GLenum returnValue;
		executeCommand(std::make_shared<GlGetErrorCommand>(returnValue));
		return returnValue;
#else
		return GL_NO_ERROR;
#endif
	}

	void FunctionWrapper::glDrawElementsNotThreadSafe(const GLenum& mode, const GLsizei& count, const GLenum& type, const void *indices)
	{
		g_glDrawElements(mode, count, type, indices);
	}

	void FunctionWrapper::glLineWidth(const GLfloat& width)
	{
		executeCommand(std::make_shared<GlLineWidthCommand>(width));
	}

	void FunctionWrapper::glClear(const GLbitfield& mask)
	{
		executeCommand(std::make_shared<GlClearCommand>(mask));
	}

	void FunctionWrapper::glGetFloatv(const GLenum& pname, GLfloat* data)
	{
		executePriorityCommand(std::make_shared<GlGetFloatvCommand>(pname, data));
	}

	void FunctionWrapper::glDeleteTextures(const GLsizei& n, std::unique_ptr<GLuint[]> textures)
	{
		executeCommand(std::make_shared<GlDeleteTexturesCommand>(n, std::move(textures)));
	}

	void FunctionWrapper::glGenTextures(const GLsizei& n, GLuint* textures)
	{
		executePriorityCommand(std::make_shared<GlGenTexturesCommand>(n, textures));
	}

	void FunctionWrapper::glTexParameterf(const GLenum& target, const GLenum& pname, const GLfloat& param)
	{
		executeCommand(std::make_shared<GlTexParameterfCommand>(target, pname, param));
	}

	void FunctionWrapper::glActiveTexture(const GLenum& texture)
	{
		executeCommand(std::make_shared<GlActiveTextureCommand>(texture));
	}

	void FunctionWrapper::glBlendColor(const GLfloat& red, const GLfloat& green, const GLfloat& blue, const GLfloat& alpha)
	{
		executeCommand(std::make_shared<GlBlendColorCommand>(red, green, blue, alpha));
	}

	void FunctionWrapper::glReadBuffer(const GLenum& src)
	{
		executeCommand(std::make_shared<GlReadBufferCommand>(src));
	}

	GLuint FunctionWrapper::glCreateShader(const GLenum& type)
	{
		GLuint returnValue;
		executeCommand(std::make_shared<GlCreateShaderCommand>(type, returnValue));
		return returnValue;
	}

	void FunctionWrapper::glCompileShader(const GLuint& shader)
	{
		executeCommand(std::make_shared<GlCompileShaderCommand>(shader));
	}

	void FunctionWrapper::glShaderSource(const GLuint& shader, const std::string& string)
	{
		executeCommand(std::make_shared<GlShaderSourceCommand>(shader, string));
	}

	GLuint FunctionWrapper::glCreateProgram(void)
	{
		GLuint returnValue;
		executeCommand(std::make_shared<GlCreateProgramCommand>(returnValue));
		return returnValue;
	}

	void FunctionWrapper::glAttachShader(const GLuint& program, const GLuint& shader)
	{
		executeCommand(std::make_shared<GlAttachShaderCommand>(program, shader));
	}

	void FunctionWrapper::glLinkProgram(const GLuint& program)
	{
		executeCommand(std::make_shared<GlLinkProgramCommand>(program));
	}

	void FunctionWrapper::glUseProgram(const GLuint& program)
	{
		executeCommand(std::make_shared<GlUseProgramCommand>(program));
	}

	GLint FunctionWrapper::glGetUniformLocation(const GLuint& program, const GLchar *name)
	{
		GLint returnValue;
		executeCommand(std::make_shared<GlGetUniformLocationCommand>(program, name, returnValue));
		return returnValue;
	}

	void FunctionWrapper::glUniform1i(const GLint& location, const GLint& v0)
	{
		executeCommand(std::make_shared<GlUniform1iCommand>(location, v0));
	}

	void FunctionWrapper::glUniform1f(const GLint& location, const GLfloat& v0)
	{
		executeCommand(std::make_shared<GlUniform1fCommand>(location, v0));
	}

	void FunctionWrapper::glUniform2f(const GLint& location, const GLfloat& v0, const GLfloat& v1)
	{
		executeCommand(std::make_shared<GlUniform2fCommand>(location, v0, v1));
	}

	void FunctionWrapper::glUniform2i(const GLint& location, const GLint& v0, const GLint& v1)
	{
		executeCommand(std::make_shared<GlUniform2iCommand>(location, v0, v1));
	}

	void FunctionWrapper::glUniform4i(const GLint& location, const GLint& v0, const GLint& v1, const GLint& v2, const GLint& v3)
	{
		executeCommand(std::make_shared<GlUniform4iCommand>(location, v0, v1, v2, v3));
	}


	void FunctionWrapper::glUniform4f(const GLint& location, const GLfloat& v0, const GLfloat& v1, const GLfloat& v2, const GLfloat& v3)
	{
		executeCommand(std::make_shared<GlUniform4fCommand>(location, v0, v1, v2, v3));
	}

	void FunctionWrapper::glUniform3fv(const GLint& location, const GLsizei& count, std::unique_ptr<GLfloat[]> value)
	{
		executeCommand(std::make_shared<GlUniform3fvCommand>(location, count, std::move(value)));
	}

	void FunctionWrapper::glUniform4fv(const GLint& location, const GLsizei& count, std::unique_ptr<GLfloat[]> value)
	{
		executeCommand(std::make_shared<GlUniform4fvCommand>(location, count, std::move(value)));
	}

	void FunctionWrapper::glDetachShader(const GLuint& program, const GLuint& shader)
	{
		executeCommand(std::make_shared<GlDetachShaderCommand>(program, shader));
	}

	void FunctionWrapper::glDeleteShader(const GLuint& shader)
	{
		executeCommand(std::make_shared<GlDeleteShaderCommand>(shader));
	}

	void FunctionWrapper::glDeleteProgram(const GLuint& program)
	{
		executeCommand(std::make_shared<GlDeleteProgramCommand>(program));
	}

	void FunctionWrapper::glGetProgramInfoLog(const GLuint& program, const GLsizei& bufSize, GLsizei* length, GLchar *infoLog)
	{
		executeCommand(std::make_shared<GlGetProgramInfoLogCommand>(program, bufSize, length, infoLog));
	}

	void FunctionWrapper::glGetShaderInfoLog(const GLuint& shader, const GLsizei& bufSize, GLsizei* length, GLchar *infoLog)
	{
		executeCommand(std::make_shared<GlGetShaderInfoLogCommand>(shader, bufSize, length, infoLog));
	}

	void FunctionWrapper::glGetShaderiv(const GLuint& shader, const GLenum& pname, GLint* params)
	{
		executeCommand(std::make_shared<GlGetShaderivCommand>(shader, pname, params));
	}

	void FunctionWrapper::glGetProgramiv(const GLuint& program, const GLenum& pname, GLint* params)
	{
		executeCommand(std::make_shared<GlGetProgramivCommand>(program, pname, params));
	}

	void FunctionWrapper::glEnableVertexAttribArray(const GLuint& index)
	{
		executeCommand(std::make_shared<GlEnableVertexAttribArrayCommand>(index));
	}

	void FunctionWrapper::glDisableVertexAttribArray(const GLuint& index)
	{
		executeCommand(std::make_shared<GlDisableVertexAttribArrayCommand>(index));
	}

	void FunctionWrapper::glVertexAttribPointerBuffered(const GLuint& index, const GLint& size, const GLenum& type, const GLboolean& normalized, const GLsizei& stride, std::size_t offset)
	{
		executeCommand(std::make_shared<GlVertexAttribPointerBufferedCommand>(index, size, type, normalized, stride, offset));
	}

	void FunctionWrapper::glVertexAttribPointerNotThreadSafe(const GLuint& index, const GLint& size, const GLenum& type, const GLboolean& normalized,
		const GLsizei& stride, const void *pointer)
	{
		g_glVertexAttribPointer(index, size, type, normalized, stride, pointer);
	}

	void FunctionWrapper::glVertexAttribPointerUnbuffered(const GLuint& index, const GLint& size, const GLenum& type, const GLboolean& normalized, const GLsizei& stride,
		std::size_t offset)
	{
		executeCommand(std::make_shared<GlVertexAttribPointerUnbufferedCommand>(index, size, type, normalized, stride, offset));
	}

	void FunctionWrapper::glBindAttribLocation(const GLuint& program, const GLuint& index, const std::string& name)
	{
		executeCommand(std::make_shared<GlBindAttribLocationCommand>(program, index, std::move(name)));
	}

	void FunctionWrapper::glVertexAttrib1f(const GLuint& index, const GLfloat& x)
	{
		executeCommand(std::make_shared<GlVertexAttrib1fCommand>(index, x));
	}

	void FunctionWrapper::glVertexAttrib1fNotThreadSafe(const GLuint& index, const GLfloat& x)
	{
		g_glVertexAttrib1f(index, x);
	}

	void FunctionWrapper::glVertexAttrib4f(const GLuint& index, const GLfloat& x, const GLfloat& y, const GLfloat& z, const GLfloat& w)
	{
		executeCommand(std::make_shared<GlVertexAttrib4fCommand>(index, x, y, z, w));
	}

	void FunctionWrapper::glVertexAttrib4fv(const GLuint& index, std::unique_ptr<GLfloat[]> v)
	{
		executeCommand(std::make_shared<GlVertexAttrib4fvCommand>(index, std::move(v)));
	}


	void FunctionWrapper::glDepthRangef(const GLfloat& n, const GLfloat& f)
	{
		executeCommand(std::make_shared<GlDepthRangefCommand>(n, f));
	}

	void FunctionWrapper::glClearDepthf(const GLfloat& d)
	{
		executeCommand(std::make_shared<GlClearDepthfCommand>(d));
	}


	void FunctionWrapper::glDrawBuffers(const GLsizei& n, std::unique_ptr<GLenum[]> bufs)
	{
		executeCommand(std::make_shared<GlDrawBuffersCommand>(n, std::move(bufs)));
	}

	void FunctionWrapper::glGenFramebuffers(const GLsizei& n, GLuint* framebuffers)
	{
		executePriorityCommand(std::make_shared<GlGenFramebuffersCommand>(n, framebuffers));
	}

	void FunctionWrapper::glBindFramebuffer(const GLenum& target, const GLuint& framebuffer)
	{
		executeCommand(std::make_shared<GlBindFramebufferCommand>(target, framebuffer));
	}

	void FunctionWrapper::glDeleteFramebuffers(const GLsizei& n, std::unique_ptr<GLuint[]> framebuffers)
	{
		executeCommand(std::make_shared<GlDeleteFramebuffersCommand>(n, std::move(framebuffers)));
	}

	void FunctionWrapper::glFramebufferTexture2D(const GLenum& target, const GLenum& attachment, const GLenum& textarget, const GLuint& texture, const GLint& level)
	{
		executeCommand(std::make_shared<GlFramebufferTexture2DCommand>(target, attachment, textarget, texture, level));
	}

	void FunctionWrapper::glTexImage2DMultisample(const GLenum& target, const GLsizei& samples, const GLenum& internalformat, const GLsizei& width, const GLsizei& height, const GLboolean& fixedsamplelocations)
	{
		executeCommand(std::make_shared<GlTexImage2DMultisampleCommand>(target, samples, internalformat, width, height, fixedsamplelocations));
	}

	void FunctionWrapper::glTexStorage2DMultisample(const GLenum& target, const GLsizei& samples, const GLenum& internalformat, const GLsizei& width, const GLsizei& height, const GLboolean& fixedsamplelocations)
	{
		executeCommand(std::make_shared<GlTexStorage2DMultisampleCommand>(target, samples, internalformat, width, height, fixedsamplelocations));
	}

	void FunctionWrapper::glGenRenderbuffers(const GLsizei& n, GLuint* renderbuffers)
	{
		executePriorityCommand(std::make_shared<GlGenRenderbuffersCommand>(n, renderbuffers));
	}

	void FunctionWrapper::glBindRenderbuffer(const GLenum& target, const GLuint& renderbuffer)
	{
		executeCommand(std::make_shared<GlBindRenderbufferCommand>(target, renderbuffer));
	}

	void FunctionWrapper::glRenderbufferStorage(const GLenum& target, const GLenum& internalformat, const GLsizei& width, const GLsizei& height)
	{
		executeCommand(std::make_shared<GlRenderbufferStorageCommand>(target, internalformat, width, height));
	}

	void FunctionWrapper::glDeleteRenderbuffers(const GLsizei& n, std::unique_ptr<GLuint[]> renderbuffers)
	{
		executeCommand(std::make_shared<GlDeleteRenderbuffersCommand>(n, std::move(renderbuffers)));
	}

	void FunctionWrapper::glFramebufferRenderbuffer(const GLenum& target, const GLenum& attachment, const GLenum& renderbuffertarget, const GLuint& renderbuffer)
	{
		executeCommand(std::make_shared<GlFramebufferRenderbufferCommand>(target, attachment, renderbuffertarget, renderbuffer));
	}

	GLenum FunctionWrapper::glCheckFramebufferStatus(const GLenum& target)
	{
#ifdef GL_DEBUG
		GLenum returnValue;
		executeCommand(std::make_shared<GlCheckFramebufferStatusCommand>(target, returnValue));
		return returnValue;
#else
		return GL_FRAMEBUFFER_COMPLETE;
#endif
	}

	void FunctionWrapper::glBlitFramebuffer(const GLint& srcX0, const GLint& srcY0, const GLint& srcX1, const GLint& srcY1, const GLint& dstX0, const GLint& dstY0, const GLint& dstX1, const GLint& dstY1, const GLbitfield& mask, const GLenum& filter)
	{
		executeCommand(std::make_shared<GlBlitFramebufferCommand>(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter));
	}

	void FunctionWrapper::glGenVertexArrays(const GLsizei& n, GLuint* arrays)
	{
		executePriorityCommand(std::make_shared<GlGenVertexArraysCommand>(n, arrays));
	}

	void FunctionWrapper::glBindVertexArray(const GLuint& array)
	{
		executeCommand(std::make_shared<GlBindVertexArrayCommand>(array));
	}

	void FunctionWrapper::glDeleteVertexArrays(const GLsizei& n, std::unique_ptr<GLuint[]> arrays)
	{
		executeCommand(std::make_shared<GlDeleteVertexArraysCommand>(n, std::move(arrays)));
	}

	void FunctionWrapper::glGenBuffers(GLsizei n, GLuint* buffers)
	{
		executePriorityCommand(std::make_shared<GlGenBuffersCommand>(n, buffers));
	}

	void FunctionWrapper::glBindBuffer(const GLenum& target, const GLuint& buffer)
	{
		executeCommand(std::make_shared<GlBindBufferCommand>(target, buffer));
	}

	void FunctionWrapper::glMapBuffer(const GLenum& target, const GLenum& access)
	{
		executeCommand(std::make_shared<GlMapBufferCommand>(target, access));
	}

	void* FunctionWrapper::glMapBufferRange(const GLenum& target, const GLintptr& offset, const GLsizeiptr& length, const GLbitfield& access)
	{
		GLubyte* returnValue;
		executeCommand(std::make_shared<GlMapBufferRangeCommand>(target, offset, length, access, returnValue));
		return returnValue;
	}

	void FunctionWrapper::glMapBufferRangeWriteAsync(const GLenum& target, const GLuint& buffer, const GLintptr& offset, u32 length, const GLbitfield& access, std::unique_ptr<u8[]> data)
	{
		executeCommand(std::make_shared<GlMapBufferRangeWriteAsyncCommand>(target, buffer, offset, length, access, std::move(data)));
	}

	void* FunctionWrapper::glMapBufferRangeReadAsync(const GLenum& target, const GLuint& buffer, const GLintptr& offset, u32 length, const GLbitfield& access)
	{
		executeCommand(std::make_shared<GlMapBufferRangeReadAsyncCommand>(target, buffer, offset, length, access));
		return GlMapBufferRangeReadAsyncCommand::getData(buffer);
	}

	GLboolean FunctionWrapper::glUnmapBuffer(const GLenum& target)
	{
		GLboolean returnValue;
		executeCommand(std::make_shared<GlUnmapBufferCommand>(target, returnValue));
		return returnValue;
	}

	void FunctionWrapper::glUnmapBufferAsync(const GLenum& target)
	{
		executeCommand(std::make_shared<GlUnmapBufferAsyncCommand>(target));
	}

	void FunctionWrapper::glDeleteBuffers(GLsizei n, std::unique_ptr<GLuint[]> buffers)
	{
		executeCommand(std::make_shared<GlDeleteBuffersCommand>(n, std::move(buffers)));
	}

	void FunctionWrapper::glBindImageTexture(const GLuint& unit, const GLuint& texture, const GLint& level, const GLboolean& layered, const GLint& layer, const GLenum& access, const GLenum& format)
	{
		executeCommand(std::make_shared<GlBindImageTextureCommand>(unit, texture, level, layered, layer, access, format));
	}

	void FunctionWrapper::glMemoryBarrier(const GLbitfield& barriers)
	{
		executeCommand(std::make_shared<GlMemoryBarrierCommand>(barriers));
	}

	const GLubyte* FunctionWrapper::glGetStringi(const GLenum& name, const GLuint& index)
	{
		const GLubyte* returnValue;
		executePriorityCommand(std::make_shared<GlGetStringiCommand>(name, index, returnValue));
		return returnValue;
	}

	void FunctionWrapper::glInvalidateFramebuffer(const GLenum& target, const GLsizei& numAttachments, std::unique_ptr<GLenum[]> attachments)
	{
		executeCommand(std::make_shared<GlInvalidateFramebufferCommand>(target, numAttachments, std::move(attachments)));
	}

	GLsync FunctionWrapper::glFenceSync(const GLenum& condition, const GLbitfield& flags)
	{
		GLsync returnValue;
		executePriorityCommand(std::make_shared<GlFenceSyncCommand>(condition, flags, returnValue));
		return returnValue;
	}

	void FunctionWrapper::glClientWaitSync(const GLsync& sync, const GLbitfield& flags, const GLuint64& timeout)
	{
		executePriorityCommand(std::make_shared<GlClientWaitSyncCommand>(sync, flags, timeout));
	}

	void FunctionWrapper::glDeleteSync(const GLsync& sync)
	{
		executeCommand(std::make_shared<GlDeleteSyncCommand>(sync));
	}

	GLuint FunctionWrapper::glGetUniformBlockIndex(const GLuint& program, GLchar *uniformBlockName)
	{
		GLuint returnValue;
		executeCommand(std::make_shared<GlGetUniformBlockIndexCommand>(program, uniformBlockName, returnValue));
		return returnValue;
	}

	void FunctionWrapper::glUniformBlockBinding(const GLuint& program, const GLuint& uniformBlockIndex, const GLuint& uniformBlockBinding)
	{
		executeCommand(std::make_shared<GlUniformBlockBindingCommand>(program, uniformBlockIndex, uniformBlockBinding));
	}

	void FunctionWrapper::glGetActiveUniformBlockiv(const GLuint& program, const GLuint& uniformBlockIndex, const GLenum& pname, GLint* params)
	{
		executeCommand(std::make_shared<GlGetActiveUniformBlockivCommand>(program, uniformBlockIndex, pname, params));
	}

	void FunctionWrapper::glGetUniformIndices(const GLuint& program, const GLsizei& uniformCount, const GLchar *const*uniformNames, GLuint* uniformIndices)
	{
		executeCommand(std::make_shared<GlGetUniformIndicesCommand>(program, uniformCount, uniformNames, uniformIndices));
	}

	void FunctionWrapper::glGetActiveUniformsiv(const GLuint& program, const GLsizei& uniformCount, const GLuint *uniformIndices, const GLenum& pname, GLint* params)
	{
		executeCommand(std::make_shared<GlGetActiveUniformsivCommand>(program, uniformCount, uniformIndices, pname, params));
	}

	void FunctionWrapper::glBindBufferBase(const GLenum& target, const GLuint& index, const GLuint& buffer)
	{
		executeCommand(std::make_shared<GlBindBufferBaseCommand>(target, index, buffer));
	}

	void FunctionWrapper::glGetProgramBinary(const GLuint& program, const GLsizei& bufSize, GLsizei* length, GLenum* binaryFormat, void *binary)
	{
		executeCommand(std::make_shared<GlGetProgramBinaryCommand>(program, bufSize, length, binaryFormat, binary));
	}

	void FunctionWrapper::glProgramParameteri(const GLuint& program, const GLenum& pname, const GLint& value)
	{
		executeCommand(std::make_shared<GlProgramParameteriCommand>(program, pname, value));
	}

	void FunctionWrapper::glTexStorage2D(const GLenum& target, const GLsizei& levels, const GLenum& internalformat, const GLsizei& width, const GLsizei& height)
	{
		executeCommand(std::make_shared<GlTexStorage2DCommand>(target, levels, internalformat, width, height));
	}

	void FunctionWrapper::glTextureStorage2D(const GLuint& texture, const GLsizei& levels, const GLenum& internalformat, const GLsizei& width, const GLsizei& height)
	{
		executeCommand(std::make_shared<GlTextureStorage2DCommand>(texture, levels, internalformat, width, height));
	}

	void FunctionWrapper::glTextureSubImage2DBuffered(const GLuint& texture, const GLint& level, const GLint& xoffset, const GLint& yoffset, const GLsizei& width, const GLsizei& height, const GLenum& format, const GLenum& type, std::size_t offset)
	{
		executeCommand(std::make_shared<GlTextureSubImage2DBufferedCommand>(texture, level, xoffset, yoffset, width, height, format, type, offset));
	}

	void FunctionWrapper::glTextureStorage2DMultisample(const GLuint& texture, const GLenum& target, const GLsizei& samples, const GLenum& internalformat, const GLsizei& width, const GLsizei& height, const GLboolean& fixedsamplelocations)
	{
		executeCommand(std::make_shared<GlTextureStorage2DMultisampleCommand>(texture, target, samples, internalformat, width, height, fixedsamplelocations));
	}

	void FunctionWrapper::glTextureParameteri(const GLuint& texture, const GLenum& pname, const GLint& param)
	{
		executeCommand(std::make_shared<GlTextureParameteriCommand>(texture, pname, param));
	}

	void FunctionWrapper::glTextureParameterf(const GLuint& texture, const GLenum& pname, const GLfloat& param)
	{
		executeCommand(std::make_shared<GlTextureParameterfCommand>(texture, pname, param));
	}

	void FunctionWrapper::glCreateTextures(const GLenum& target, const GLsizei& n, GLuint* textures)
	{
		executePriorityCommand(std::make_shared<GlCreateTexturesCommand>(target, n, textures));
	}

	void FunctionWrapper::glCreateBuffers(const GLsizei& n, GLuint* buffers)
	{
		executePriorityCommand(std::make_shared<GlCreateBuffersCommand>(n, buffers));
	}

	void FunctionWrapper::glCreateFramebuffers(const GLsizei& n, GLuint* framebuffers)
	{
		executePriorityCommand(std::make_shared<GlCreateFramebuffersCommand>(n, framebuffers));
	}

	void FunctionWrapper::glNamedFramebufferTexture(const GLuint& framebuffer, const GLenum& attachment, const GLuint& texture, const GLint& level)
	{
		executeCommand(std::make_shared<GlNamedFramebufferTextureCommand>(framebuffer, attachment, texture, level));
	}

	void FunctionWrapper::glDrawElementsBaseVertex(const GLenum& mode, const GLsizei& count, const GLenum& type, const char* indices, const GLint& basevertex)
	{
		executeCommand(std::make_shared<GlDrawElementsBaseVertexCommand>(mode, count, type, std::move(indices), basevertex));
	}

	void FunctionWrapper::glFlushMappedBufferRange(const GLenum& target, const GLintptr& offset, const GLsizeiptr& length)
	{
		executeCommand(std::make_shared<GlFlushMappedBufferRangeCommand>(target, offset, length));
	}

	void FunctionWrapper::glFinish(void)
	{
		executeCommand(std::make_shared<GlFinishCommand>());
	}

#ifdef MUPENPLUSAPI

	void FunctionWrapper::CoreVideo_Init(void)
	{
		executeCommand(std::make_shared<CoreVideoInitCommand>());
	}

	void FunctionWrapper::CoreVideo_Quit(void)
	{
		executeCommand(std::make_shared<CoreVideoQuitCommand>());

		m_shutdown = true;

		if(m_threaded_wrapper) {
			m_condition.notify_all();
			m_commandExecutionThread.join();
		}
	}

	m64p_error FunctionWrapper::CoreVideo_SetVideoMode(int screenWidth, int screenHeight, int bitsPerPixel, m64p_video_mode mode, m64p_video_flags flags)
	{
		m64p_error returnValue;
		executeCommand(std::make_shared<CoreVideoSetVideoModeCommand>(screenWidth, screenHeight, bitsPerPixel, mode, flags, returnValue));
		return returnValue;
	}

	void FunctionWrapper::CoreVideo_GL_SetAttribute(m64p_GLattr attribute, int value)
	{
		executeCommand(std::make_shared<CoreVideoGLSetAttributeCommand>(attribute, value));
	}

	void FunctionWrapper::CoreVideo_GL_GetAttribute(m64p_GLattr attribute, int *value)
	{
		executeCommand(std::make_shared<CoreVideoGLGetAttributeCommand>(attribute, value));
	}

	void FunctionWrapper::CoreVideo_GL_SwapBuffers(void)
	{
		++m_swapBuffersQueued;
		executeCommand(std::make_shared<CoreVideoGLSwapBuffersCommand>([]{ReduceSwapBuffersQueued();}));
	}
#else
	bool FunctionWrapper::windowsStart(void)
	{
		bool returnValue;
		executeCommand(std::make_shared<WindowsStartCommand>(returnValue));
		return returnValue;
	}

	void FunctionWrapper::windowsStop(void)
	{
		executeCommand(std::make_shared<WindowsStopCommand>());

		m_shutdown = true;

		if (m_threaded_wrapper) {
			m_condition.notify_all();
			m_commandExecutionThread.join();
		}
	}

	void FunctionWrapper::windowsSwapBuffers(void)
	{
		++m_swapBuffersQueued;
		executeCommand(std::make_shared<WindowsSwapBuffersCommand>([]{ReduceSwapBuffersQueued();}));
	}

#endif
	void FunctionWrapper::ReduceSwapBuffersQueued(void)
	{
		--m_swapBuffersQueued;

		if(m_swapBuffersQueued == 0)
		{
			m_condition.notify_all();
		}
	}

	void FunctionWrapper::WaitForSwapBuffersQueued(void)
	{
		std::unique_lock<std::mutex> lock(m_condvarMutex);

		if (!m_shutdown && m_swapBuffersQueued != 0) {
			m_condition.wait(lock, []{return FunctionWrapper::m_swapBuffersQueued == 0;});
		}
	}
}
