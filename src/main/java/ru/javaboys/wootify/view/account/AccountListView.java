package ru.javaboys.wootify.view.account;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import ru.javaboys.wootify.entity.Account;
import ru.javaboys.wootify.view.main.MainView;


@Route(value = "accounts", layout = MainView.class)
@ViewController(id = "Account.list")
@ViewDescriptor(path = "account-list-view.xml")
@LookupComponent("accountsDataGrid")
@DialogMode(width = "64em")
public class AccountListView extends StandardListView<Account> {
}