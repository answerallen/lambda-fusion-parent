package com.lambda.fusion.ai.runtime.tools;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.core.util.HexUtil;
import com.lambda.cloud.netty.protocol.engine.impl.ReflectionProtocolEngine;
import com.lambda.cloud.netty.protocol.scanner.ProtocolPayloadScanner;
import com.lambda.cloud.ykc.message.v16.YkcV16BasePayload;
import com.lambda.cloud.ykc.message.v16.up.YkcV16LoginUp;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 云快充协议 v1.6 帧解析工具测试：登录请求帧 round-trip、未识别帧类型、加密帧无密钥、
 * 非法/残缺/空输入降级为可读错误（工具不向模型抛异常）。
 *
 * @author zx
 */
class CloudChargingFrameParseToolTest {

    /** 无密钥：未加密帧可完整解析，加密帧提示需密钥。 */
    private final YkcFrameParseTool tool = new YkcFrameParseTool("");

    @BeforeAll
    static void registerV16() {
        // 与工具内部一致：仅注册 v16，保证 serialize 时 detail 复合字段可路由。
        new ProtocolPayloadScanner().scanAndRegister("com.lambda.cloud.ykc.message.v16");
    }

    @Test
    void shouldParseLoginFrameRoundTrip() throws Exception {
        String hex = loginFrameHex(false);

        String result = tool.parseFrame(hex);

        assertThat(result)
                .contains("登录请求")
                .contains("55031412782305") // equipmentId(BCD)
                .contains("V4.1.50") // programVersion(ASCII)
                .contains("帧类型");
    }

    @Test
    void shouldParseUnencryptedFrameFully() throws Exception {
        String hex = loginFrameHex(false);

        String result = tool.parseFrame(hex);

        // 未加密帧无需密钥即可完整解码 detail
        assertThat(result).doesNotContain("需配置密钥");
    }

    @Test
    void shouldReportEncryptedFrameNeedsKeyWhenMissingKey() throws Exception {
        String hex = loginFrameHex(true); // encryptFlag=0x01

        String result = tool.parseFrame(hex);

        assertThat(result).contains("encryptFlag=0x01").contains("未配置解密密钥");
    }

    @Test
    void shouldReportUnrecognizedFrameType() {
        // 手工帧：起始符68 长度00 序号0000 加密00 帧类型99（未定义）…，仅到 6 字节足够定位帧类型
        String hex = "680000000099";

        String result = tool.parseFrame(hex);

        assertThat(result).contains("未识别帧类型").contains("0x99");
    }

    @Test
    void shouldRejectNonHexInput() {
        assertThat(tool.parseFrame("XYZxyz")).contains("不是有效的十六进制");
    }

    @Test
    void shouldRejectEmpty() {
        assertThat(tool.parseFrame(null)).contains("帧为空");
        assertThat(tool.parseFrame("   ")).contains("帧为空");
    }

    @Test
    void shouldRejectShortFrame() {
        assertThat(tool.parseFrame("6801")).contains("帧过短");
    }

    /** 序列化一个登录请求帧返回 hex；encrypted=true 时把 encryptFlag 位置改成 0x01 模拟加密。 */
    private static String loginFrameHex(boolean encrypted) throws Exception {
        YkcV16LoginUp req = new YkcV16LoginUp();
        req.setEquipmentId("55031412782305");
        req.setEquipmentType(0);
        req.setConnectorCount(2);
        req.setProtocolVersion(15);
        req.setProgramVersion("V4.1.50");
        req.setNetworkType(1);
        req.setSimCardNumber("01010101010101010101");
        req.setOperator(4);

        YkcV16BasePayload base = new YkcV16BasePayload();
        base.setStartFlag("68");
        base.setEncryptFlag(encrypted ? "01" : "00");
        base.setFrameType("01");
        base.setSerialNumber(0);
        base.setDetail(req);

        ReflectionProtocolEngine engine = new ReflectionProtocolEngine(null);
        ByteBuf out = Unpooled.buffer();
        engine.serialize(base, out);
        byte[] raw = new byte[out.readableBytes()];
        out.readBytes(raw);
        if (encrypted) {
            // encryptFlag 位于帧头固定偏移 byte[4]
            raw[4] = 0x01;
        }
        return HexUtil.encodeHexStr(raw, false);
    }
}
