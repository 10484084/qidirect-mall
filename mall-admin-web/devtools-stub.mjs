// 生产构建 stub：绕开 rollup 4.68 解析 @vue/devtools-api ESM 时
// "Code is nested too deeply" 的解析 bug。生产构建不使用 devtools，
// 仅导出 pinia 等依赖引用到的空实现。
export function setupDevtoolsPlugin() {}
export function setupDevtoolsPluginStub() {}
export default {};
