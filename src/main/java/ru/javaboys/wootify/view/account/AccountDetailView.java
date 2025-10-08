package ru.javaboys.wootify.view.account;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.wootify.entity.Account;
import ru.javaboys.wootify.view.main.MainView;

@Route(value = "accounts/:id", layout = MainView.class)
@ViewController(id = "Account.detail")
@ViewDescriptor(path = "account-detail-view.xml")
@EditedEntityContainer("accountDc")
public class AccountDetailView extends StandardDetailView<Account> {
}