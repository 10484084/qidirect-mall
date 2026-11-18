import { ElMessage } from 'element-plus';
import { uploadFile } from '../api/request';

const ALLOWED = ['jpg', 'jpeg', 'png', 'webp', 'pdf'];
const MAX_SIZE = 5 * 1024 * 1024;

/**
 * el-upload 的 http-request 自定义实现：走统一上传接口并回填地址。
 * options.file 为原始文件，options.onSuccess(res) 由文件列表使用。
 */
export async function httpRequest(options) {
  const file = options.file;
  const ext = (file.name.split('.').pop() || '').toLowerCase();
  if (!ALLOWED.includes(ext)) {
    ElMessage.error('仅支持 jpg/png/webp/pdf 格式');
    options.onError(new Error('invalid type'));
    return;
  }
  if (file.size > MAX_SIZE) {
    ElMessage.error('文件不能超过 5MB');
    options.onError(new Error('too large'));
    return;
  }
  try {
    const url = await uploadFile(file);
    options.onSuccess(url);
  } catch (e) {
    options.onError(e);
  }
}

export function isImage(url) {
  return /\.(jpg|jpeg|png|webp)$/i.test(url || '');
}
