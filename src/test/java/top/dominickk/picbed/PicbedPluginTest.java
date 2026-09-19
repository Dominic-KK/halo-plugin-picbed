package top.dominickk.picbed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.plugin.PluginContext;

@ExtendWith(MockitoExtension.class)
class PicbedPluginTest {

    @Mock
    PluginContext context;

    @InjectMocks
    PicbedPlugin plugin;

    @Test
    void contextLoads() {
        plugin.start();
        plugin.stop();
    }
}
