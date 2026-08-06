// 支付工具
// 后端 PayService.createPayment 返回支付宝 pageExecute 生成的完整 HTML（含 <form>）。
// 将表单解析并提交，触发浏览器顶层跳转到支付宝（沙盒）收银台。
export function submitPayForm(payForm) {
  if (!payForm) {
    throw new Error('支付表单为空')
  }
  const doc = new DOMParser().parseFromString(payForm, 'text/html')
  const form = doc.querySelector('form')
  if (!form) {
    throw new Error('支付宝表单解析失败')
  }
  // form 是孤儿节点，追加到当前 document 后 submit() 发起顶层导航
  document.body.appendChild(form)
  form.submit()
}
