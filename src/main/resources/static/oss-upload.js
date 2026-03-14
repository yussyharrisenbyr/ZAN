(() => {
  const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']);

  function normalizeMessage(payload, fallback) {
    return payload?.message || fallback;
  }

  async function parseJsonSafely(response) {
    try {
      return await response.json();
    } catch (error) {
      return null;
    }
  }

  function validateImageFile(file) {
    if (!(file instanceof File)) {
      throw new Error('请选择要上传的图片');
    }
    if (!IMAGE_TYPES.has(String(file.type || '').toLowerCase())) {
      throw new Error('仅支持 JPG、PNG、WEBP、GIF 图片');
    }
    if (!file.size) {
      throw new Error('图片内容不能为空');
    }
  }

  async function requestPresignedUpload(file, bizType) {
    const response = await fetch('/oss/presign', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        bizType,
        fileName: file.name,
        contentType: file.type,
        fileSize: file.size
      })
    });
    const payload = await parseJsonSafely(response);
    if (!response.ok || payload?.code !== 0 || !payload?.data?.uploadUrl) {
      throw new Error(normalizeMessage(payload, '获取上传签名失败'));
    }
    return payload.data;
  }

  async function uploadBySignedUrl(uploadUrl, file, contentType = '') {
    const normalizedContentType = String(contentType || file?.type || '').trim();
    const response = await fetch(uploadUrl, {
      method: 'PUT',
      body: file,
      headers: normalizedContentType ? { 'Content-Type': normalizedContentType } : undefined
    });
    if (!response.ok) {
      throw new Error('上传到 OSS 失败，请检查 Bucket CORS 或签名配置');
    }
  }

  async function uploadImage(file, bizType) {
    validateImageFile(file);
    const signed = await requestPresignedUpload(file, bizType);
    await uploadBySignedUrl(signed.uploadUrl, file, signed.contentType);
    return signed;
  }

  window.SiteOssUpload = {
    uploadImage
  };
})();

