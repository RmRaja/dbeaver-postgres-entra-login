package io.github.rmraja.dbeaver.entraid.ui;

import io.github.rmraja.dbeaver.entraid.EntraIdAuthModel;
import io.github.rmraja.dbeaver.entraid.EntraIdSettings;
import io.github.rmraja.dbeaver.entraid.oauth.SignInFlow;
import io.github.rmraja.dbeaver.entraid.oauth.TokenRequest;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.ui.IObjectPropertyConfigurator;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.utils.CommonUtils;

/**
 * Connection page panel shown when "Microsoft Entra ID (Azure)" authentication is selected.
 */
public class EntraIdAuthConfigurator implements IObjectPropertyConfigurator<Object, DBPDataSourceContainer> {

    private Text usernameText;
    private Combo flowCombo;
    private Text tenantText;
    private Text clientIdText;

    @Override
    public void createControl(Composite parent, Object object, Runnable propertyChangeListener) {
        usernameText = createText(parent, "Username:",
            "Your Entra ID user (e.g. user@contoso.com), or the Entra group name mapped to a PostgreSQL role",
            propertyChangeListener);

        UIUtils.createLabel(parent, "Sign-in method:").setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
        flowCombo = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        for (SignInFlow flow : SignInFlow.values()) {
            flowCombo.add(flow.getLabel());
        }
        flowCombo.setToolTipText("Interactive browser opens the Microsoft sign-in page; "
            + "device code shows a code to enter at microsoft.com/devicelogin");
        flowCombo.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
        flowCombo.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> propertyChangeListener.run()));

        tenantText = createText(parent, "Tenant:",
            "Directory (tenant) ID or domain, e.g. contoso.onmicrosoft.com. "
                + "Leave empty for '" + TokenRequest.DEFAULT_TENANT + "' (any work or school account)",
            propertyChangeListener);
        tenantText.setMessage(TokenRequest.DEFAULT_TENANT);

        clientIdText = createText(parent, "Client ID:",
            "Application (client) ID used to sign in. Leave empty to use the Azure CLI public client",
            propertyChangeListener);
        clientIdText.setMessage(TokenRequest.DEFAULT_CLIENT_ID + " (Azure CLI)");

        new Label(parent, SWT.NONE);
        Button clearButton = UIUtils.createPushButton(parent, "Clear cached sign-in", null,
            SelectionListener.widgetSelectedAdapter(e -> {
                EntraIdAuthModel.getTokenProvider().signOut(currentRequest());
                UIUtils.showMessageBox(parent.getShell(), "Microsoft Entra ID",
                    "Cached sign-in for this account was cleared. You will be asked to sign in on the next connect.",
                    SWT.ICON_INFORMATION);
            }));
        clearButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
    }

    private static Text createText(Composite parent, String label, String tooltip, Runnable listener) {
        UIUtils.createLabel(parent, label).setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
        Text text = new Text(parent, SWT.BORDER);
        text.setToolTipText(tooltip);
        text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        text.addModifyListener(e -> listener.run());
        return text;
    }

    private TokenRequest currentRequest() {
        return new TokenRequest(selectedFlow(), tenantText.getText(), clientIdText.getText(), usernameText.getText());
    }

    private SignInFlow selectedFlow() {
        int index = flowCombo.getSelectionIndex();
        return index < 0 ? SignInFlow.BROWSER : SignInFlow.values()[index];
    }

    @Override
    public void loadSettings(DBPDataSourceContainer dataSource) {
        DBPConnectionConfiguration cfg = dataSource.getConnectionConfiguration();
        usernameText.setText(CommonUtils.notEmpty(cfg.getUserName()));
        flowCombo.select(SignInFlow.fromString(cfg.getAuthProperty(EntraIdSettings.PROP_FLOW)).ordinal());
        tenantText.setText(CommonUtils.notEmpty(cfg.getAuthProperty(EntraIdSettings.PROP_TENANT)));
        clientIdText.setText(CommonUtils.notEmpty(cfg.getAuthProperty(EntraIdSettings.PROP_CLIENT_ID)));
    }

    @Override
    public void saveSettings(DBPDataSourceContainer dataSource) {
        DBPConnectionConfiguration cfg = dataSource.getConnectionConfiguration();
        cfg.setUserName(usernameText.getText().trim());
        cfg.setUserPassword(null);
        cfg.setAuthProperty(EntraIdSettings.PROP_FLOW, selectedFlow().name());
        cfg.setAuthProperty(EntraIdSettings.PROP_TENANT, tenantText.getText().trim());
        cfg.setAuthProperty(EntraIdSettings.PROP_CLIENT_ID, clientIdText.getText().trim());
        // Nothing secret is stored on the connection; tokens live in secure storage
        dataSource.setSavePassword(true);
    }

    @Override
    public void resetSettings(DBPDataSourceContainer dataSource) {
        loadSettings(dataSource);
    }

    @Override
    public boolean isComplete() {
        return usernameText != null && !usernameText.getText().isBlank();
    }

    @Override
    public String getErrorMessage() {
        return isComplete() ? null : "Enter your Microsoft Entra ID user name";
    }
}
