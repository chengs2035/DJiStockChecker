import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DJI商城商品库存检查器
 * <p>
 * 这个类用于自动监控DJI商城多个商品的库存状态。
 * 当任何商品有货时，会通过企业微信机器人发送通知。
 * </p>
 * 
 * @author djc8
 * @version 1.1.0
 */
public class DjiStockChecker {

    private static final Logger logger = LoggerFactory.getLogger(DjiStockChecker.class);
    
    // 配置参数
    private static final List<ProductConfig> products = new ArrayList<>();
    private static int minIntervalMinutes = 3;
    private static int maxIntervalMinutes = 6;
    private static String targetClass = "info-section";
    private static String targetText = "缺货";
    private static String webhookUrl;
    private static String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static long notificationIntervalMillis = TimeUnit.MINUTES.toMillis(60); // 默认通知间隔1小时

    /**
     * 程序入口点
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        try {
            logger.info("正在启动DJI商品库存检查器...");
            loadConfig();
            disableSSLVerification(); // 简化HTTPS验证
            startScheduler();
        } catch (Exception e) {
            logger.error("程序启动失败: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * 加载配置文件
     * 从resources目录下的config.properties文件中加载配置
     *
     * @throws IOException 如果配置文件读取失败
     */
    private static void loadConfig() throws IOException {

        Properties props = new Properties();

        try (InputStream  fis = DjiStockChecker.class.getClassLoader().getResourceAsStream("config.properties");

            InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
           
            props.load(reader);
            
            // 加载商品配置
            loadProductConfigs(props);
            
            // 加载其他配置
            webhookUrl = props.getProperty("webhook.url");
            
            // 读取可选配置，使用默认值
            minIntervalMinutes = Integer.parseInt(
                props.getProperty("check.interval.min", "3"));
            maxIntervalMinutes = Integer.parseInt(
                props.getProperty("check.interval.max", "6"));
            targetClass = props.getProperty("target.class", "info-section");
            targetText = props.getProperty("target.text", "缺货");
            userAgent = props.getProperty("http.user-agent", userAgent);
            notificationIntervalMillis = TimeUnit.MINUTES.toMillis(
                Long.parseLong(props.getProperty("notification.interval", "60")));
            
            validateConfig();
        }
    }
    
    /**
     * 加载商品配置
     * 从配置文件中读取所有配置的商品信息
     *
     * @param props 配置属性对象
     */
    private static void loadProductConfigs(Properties props) {
        products.clear();
        int index = 1;
        
        while (true) {
            String nameKey = String.format("product.%d.name", index);
            String urlKey = String.format("product.%d.url", index);
            
            String name = props.getProperty(nameKey);
            String url = props.getProperty(urlKey);
            
            if (name == null || url == null) {
                break; // 没有更多商品配置
            }
            
            if (!name.trim().isEmpty() && !url.trim().isEmpty()) {
                products.add(new ProductConfig(name.trim(), url.trim()));
                logger.info("已加载商品配置: {}", name);
            }
            
            index++;
        }
    }
    
    /**
     * 验证必要的配置参数
     *
     * @throws IllegalStateException 如果必要的配置参数缺失
     */
    private static void validateConfig() {
        if (products.isEmpty()) {
            throw new IllegalStateException("未配置任何商品信息");
        }
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            throw new IllegalStateException("企业微信Webhook URL未配置");
        }
        logger.info("已加载{}个商品配置", products.size());
    }

    /**
     * 禁用SSL验证
     * 注意：这种方式仅用于开发测试环境，生产环境应该使用proper的SSL证书验证
     */
    private static void disableSSLVerification() {
        try {
            logger.info("配置SSL验证...");
            // 创建一个不验证证书链的trust manager
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            // 安装all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // 创建all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            // 安装all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            logger.info("SSL验证配置完成");
        } catch (Exception e) {
            logger.error("SSL验证配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("SSL配置失败", e);
        }
    }

    /**
     * 启动调度器
     * 使用ScheduledExecutorService来定期执行库存检查任务
     */
    private static void startScheduler() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    // 执行实际的任务
                    new StockCheckTask().run();

                    // 计算下一次执行的随机延迟
                    int nextDelay = minIntervalMinutes + 
                        (int)(Math.random() * (maxIntervalMinutes - minIntervalMinutes));
                    logger.info("下次检查将在{}分钟后进行", nextDelay);
                    executor.schedule(this, nextDelay, TimeUnit.MINUTES);
                } catch (Exception e) {
                    logger.error("任务执行失败: {}", e.getMessage(), e);
                    // 发生错误时，1分钟后重试
                    executor.schedule(this, 1, TimeUnit.MINUTES);
                }
            }
        };

        // 初始执行，延迟0分钟
        executor.schedule(task, 0, TimeUnit.MINUTES);
        logger.info("调度器已启动");
    }

    /**
     * 库存检查任务
     * 负责检查所有配置商品的库存状态并在发现有货时发送通知
     */
    static class StockCheckTask extends TimerTask {
        private static final Logger taskLogger = LoggerFactory.getLogger(StockCheckTask.class);

        @Override
        public void run() {
            taskLogger.debug("开始检查所有商品库存状态...");
            List<ProductConfig> availableProducts = new ArrayList<>();

            for (ProductConfig product : products) {
                try {
                    if (checkProductStock(product)) {
                        availableProducts.add(product);
                    }
                    // 添加随机延迟，避免请求过于频繁
                    Thread.sleep(1000 + (long)(Math.random() * 2000));
                } catch (Exception e) {
                    taskLogger.error("检查商品[{}]库存时发生错误: {}", 
                        product.getName(), e.getMessage(), e);
                }
            }

            // 发送汇总通知
            if (!availableProducts.isEmpty()) {
                sendAvailabilityNotification(availableProducts);
            }
        }

        /**
         * 检查单个商品的库存状态
         *
         * @param product 商品配置
         * @return 是否有货
         */
        private boolean checkProductStock(ProductConfig product) throws IOException {
            taskLogger.debug("正在检查商品[{}]的库存状态...", product.getName());
            
            Document doc = Jsoup.connect(product.getUrl())
                .userAgent(userAgent)
                .timeout(15000)
                .referrer("https://www.dji.com/")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .get();

            Elements stockElements = doc.select("section." + targetClass + " p");
            if (!stockElements.isEmpty()) {
                boolean isOutOfStock = false;
                for (org.jsoup.nodes.Element element : stockElements) {
                    if (element.text().contains(targetText)) {
                        isOutOfStock = true;
                        break;
                    }
                }
                
                if (!isOutOfStock) {
                    taskLogger.info("商品[{}]已有货！", product.getName());
                    return true;
                } else {
                    taskLogger.debug("商品[{}]仍然缺货", product.getName());
                    return false;
                }
            } else {
                taskLogger.warn("商品[{}]未找到库存状态元素，可能页面结构已变化", product.getName());
                return false;
            }
        }

        /**
         * 发送商品库存通知
         *
         * @param availableProducts 有货的商品列表
         */
        private void sendAvailabilityNotification(List<ProductConfig> availableProducts) {
            long currentTime = System.currentTimeMillis();
            StringBuilder message = new StringBuilder("以下商品已有货：\n\n");
            boolean shouldNotify = false;

            for (ProductConfig product : availableProducts) {
                if (currentTime - product.getLastNotificationTime() >= notificationIntervalMillis) {
                     message.append(String.format("- %s  [查看商品](%s)\n\n",  // 使用Markdown超链接格式
                            product.getName(), product.getUrl()));
                    product.updateLastNotificationTime();
                    shouldNotify = true;
                }
            }

            if (shouldNotify) {
                taskLogger.info("发送库存通知...");
                try {
                    WeChatGroupBot.sendMessage(
                        webhookUrl,
                        "DJI商品有货提醒",
                        message.toString().trim()
                    );
                } catch (Exception e) {
                    taskLogger.error("发送通知失败: {}", e.getMessage(), e);
                }
            } else {
                taskLogger.debug("有商品有货，但都在通知间隔期内，暂不重复通知");
            }
        }
    }
}