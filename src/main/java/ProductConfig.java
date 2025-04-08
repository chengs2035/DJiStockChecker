import java.util.Objects;

/**
 * 商品监控配置类
 * 封装单个商品的监控配置信息
 */
public class ProductConfig {
    private final String name;        // 商品名称
    private final String url;         // 商品URL
    private long lastNotificationTime; // 上次通知时间

    /**
     * 创建商品监控配置
     *
     * @param name 商品名称
     * @param url  商品URL
     */
    public ProductConfig(String name, String url) {
        this.name = name;
        this.url = url;
        this.lastNotificationTime = 0;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public long getLastNotificationTime() {
        return lastNotificationTime;
    }

    public void updateLastNotificationTime() {
        this.lastNotificationTime = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductConfig that = (ProductConfig) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return String.format("ProductConfig{name='%s', url='%s'}", name, url);
    }
}