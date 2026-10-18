package io.github.rmraja.dbeaver.entraid.ui;

import io.github.rmraja.dbeaver.entraid.oauth.DeviceCodeInfo;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.jkiss.dbeaver.ui.UIUtils;

/**
 * Non-modal dialog showing the device code while the connect job polls for sign-in completion.
 */
class DeviceCodeDialog extends Dialog {

    private static final int OPEN_BROWSER_ID = IDialogConstants.CLIENT_ID + 1;

    private final DeviceCodeInfo info;
    private final Runnable onCancel;
    private Font codeFont;

    DeviceCodeDialog(Shell parentShell, DeviceCodeInfo info, Runnable onCancel) {
        super(parentShell);
        this.info = info;
        this.onCancel = onCancel;
        setShellStyle(SWT.DIALOG_TRIM | SWT.MODELESS | SWT.ON_TOP);
        setBlockOnOpen(false);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Microsoft Entra ID sign-in");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(1, false));

        Link link = new Link(area, SWT.WRAP);
        link.setText("To connect, open <a>" + info.verificationUri() + "</a> and enter this code:");
        link.addListener(SWT.Selection, e -> Program.launch(info.verificationUri()));

        Text codeText = new Text(area, SWT.READ_ONLY | SWT.CENTER);
        FontData[] fontData = codeText.getFont().getFontData();
        for (FontData fd : fontData) {
            fd.setHeight(fd.getHeight() * 2);
            fd.setStyle(SWT.BOLD);
        }
        codeFont = new Font(codeText.getDisplay(), fontData);
        codeText.setFont(codeFont);
        codeText.setText(info.userCode());
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.verticalIndent = 10;
        codeText.setLayoutData(gd);

        Label hint = new Label(area, SWT.WRAP);
        hint.setText("DBeaver continues automatically once you have signed in.");
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.verticalIndent = 10;
        gd.widthHint = 380;
        hint.setLayoutData(gd);

        area.addDisposeListener(e -> codeFont.dispose());
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, OPEN_BROWSER_ID, "Copy code && open browser", true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == OPEN_BROWSER_ID) {
            UIUtils.setClipboardContents(getShell().getDisplay(), TextTransfer.getInstance(), info.userCode());
            Program.launch(info.verificationUri());
            return;
        }
        super.buttonPressed(buttonId);
    }

    @Override
    protected void cancelPressed() {
        onCancel.run();
        super.cancelPressed();
    }

    @Override
    protected void handleShellCloseEvent() {
        onCancel.run();
        super.handleShellCloseEvent();
    }
}
