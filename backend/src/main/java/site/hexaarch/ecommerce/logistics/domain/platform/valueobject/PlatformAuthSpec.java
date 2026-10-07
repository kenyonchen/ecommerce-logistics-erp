package site.hexaarch.ecommerce.logistics.domain.platform.valueobject;

import java.util.List;

/**
 * 一种对接协议的凭据要求与配置元数据。
 * <p>
 * 存在的理由有两个：
 * <ol>
 *   <li>把 {@code PlatformIntegrationServiceImpl} 里按协议分叉的 if-else 校验收敂成一张表，
 *       新增平台不必再改校验分支；</li>
 *   <li>通过 {@code GET /api/platform-integrations/auth-specs} 暴露给配置界面，
 *       让「这家要填哪几把钥匙、去哪拿」有唯一口径，而不是散落在文档和前端硬编码里。</li>
 * </ol>
 * {@code docUrls} 只收录核实过的官方文档地址；核实不到的平台（如 Temu 的签名规则只存在于需登录的
 * 文档站）在这里留空并被 {@code verified=false} 标记，避免把一个猜测的链接当成依据。
 *
 * @param type           协议
 * @param label          中文名
 * @param requiredFields 必填字段（取 {@code PlatformIntegrationSaveRequest} 的属性名）
 * @param optionalFields 选填字段，填了效果更好（如可避免每次外呼都刷新令牌）
 * @param hostFromConfig 平台 host 是否来自配置/内置常量（true 时 {@code storeUrl} 不是必填）
 * @param regionOptions  区域/站点候选值，空表示不需要区域
 * @param tokenGuide     凭据获取步骤说明（尤其「怎么拿到第一个 refresh_token」这类一次性动作）
 * @param docUrls        官方文档地址
 * @param verified       该协议的接口规范是否已按公开可读的官方文档核实
 * @param note           落地状态与已知缺口
 * @author kenyon
 */
public record PlatformAuthSpec(
        PlatformAuthType type,
        String label,
        List<String> requiredFields,
        List<String> optionalFields,
        boolean hostFromConfig,
        List<String> regionOptions,
        String tokenGuide,
        List<String> docUrls,
        boolean verified,
        String note
) {
}
