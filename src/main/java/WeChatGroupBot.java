import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信群机器人消息发送工具类
 */
public class WeChatGroupBot {
    private static final Logger logger = LoggerFactory.getLogger(WeChatGroupBot.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发送消息到企业微信群
     *
     * @param webhookUrl 机器人的Webhook地址
     * @param title      消息标题
     * @param content    消息内容
     * @throws IOException 如果消息发送失败
     */
    public static void sendMessage(String webhookUrl, String title, String content) throws IOException {
        
        logger.debug("准备发送消息，标题：{}", title);

        Map<String, Object> markdown = new HashMap<>();
        markdown.put("content", String.format("## %s\n%s", title, content));

        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "markdown");
        message.put("markdown", markdown);

        String jsonBody = objectMapper.writeValueAsString(message);
        
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(webhookUrl);
            httpPost.setHeader("Content-Type", "application/json; charset=utf-8");
            httpPost.setEntity(new StringEntity(jsonBody, "UTF-8"));

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    logger.info("消息发送成功");
                } else {
                    logger.error("消息发送失败，HTTP状态码：{}", statusCode);
                    throw new IOException("消息发送失败，HTTP状态码：" + statusCode);
                }
            }
        } catch (Exception e) {
            logger.error("发送消息时发生错误: {}", e.getMessage(), e);
            throw new IOException("发送消息失败: " + e.getMessage(), e);
        }
    }
}