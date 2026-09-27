package top.dominickk.picbed;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * <p>Plugin main class to manage the lifecycle of the plugin.</p>
 * <p>This class must be public and have a public constructor.</p>
 * <p>Only one main class extending {@link BasePlugin} is allowed per plugin.</p>
 *
 * @author Dominic-kk
 * @since 1.0.0
 */
@Component
public class PicbedPlugin extends BasePlugin {

    private final PolicyConfigWatcher policyConfigWatcher;

    public PicbedPlugin(PluginContext pluginContext, PolicyConfigWatcher policyConfigWatcher) {
        super(pluginContext);
        this.policyConfigWatcher = policyConfigWatcher;
    }

    @Override
    public void start() {
        System.out.println("插件启动成功！");
    }

    @Override
    public void stop() {
        // 停用/卸载时释放策略配置监听，避免残留后台监听
        if (policyConfigWatcher != null) {
            policyConfigWatcher.dispose();
        }
        System.out.println("插件停止！");
    }
}
