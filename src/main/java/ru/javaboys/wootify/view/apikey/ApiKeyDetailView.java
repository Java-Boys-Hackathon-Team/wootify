package ru.javaboys.wootify.view.apikey;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.wootify.entity.ApiKey;
import ru.javaboys.wootify.view.main.MainView;

@Route(value = "api-keys/:id", layout = MainView.class)
@ViewController(id = "ApiKey.detail")
@ViewDescriptor(path = "api-key-detail-view.xml")
@EditedEntityContainer("apiKeyDc")
public class ApiKeyDetailView extends StandardDetailView<ApiKey> {
}