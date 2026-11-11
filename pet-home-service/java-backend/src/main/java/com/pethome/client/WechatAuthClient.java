package com.pethome.client;

/**
 * 微信登录凭证校验客户端。
 *
 * <p>客户端只提交一次性 code，由服务端换取 openid。openid 绝不能由客户端直接提供，
 * 否则任何人都能伪造身份。</p>
 *
 * @author pet-home
 */
public interface WechatAuthClient {

    /**
     * 用小程序登录 code 换取 openid
     *
     * @param code wx.login 获取的一次性凭证
     * @return 微信用户在该小程序下的 openid
     */
    String code2Openid(String code);

}
