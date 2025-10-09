package ru.javaboys.wootify.view.apikey;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import ru.javaboys.wootify.entity.ApiKey;
import ru.javaboys.wootify.view.main.MainView;


@Route(value = "api-keys", layout = MainView.class)
@ViewController(id = "ApiKey.list")
@ViewDescriptor(path = "api-key-list-view.xml")
@LookupComponent("apiKeysDataGrid")
@DialogMode(width = "64em")
public class ApiKeyListView extends StandardListView<ApiKey> {
}