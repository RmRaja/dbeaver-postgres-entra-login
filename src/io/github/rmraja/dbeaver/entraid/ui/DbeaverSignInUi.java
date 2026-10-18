package io.github.rmraja.dbeaver.entraid.ui;

import io.github.rmraja.dbeaver.entraid.oauth.DeviceCodeInfo;
import io.github.rmraja.dbeaver.entraid.oauth.SignInUi;
import org.eclipse.swt.program.Program;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.ui.UIUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sign-in callbacks for a connect job: progress goes to the job monitor, browser and dialogs to the UI thread.
 */
public class DbeaverSignInUi implements SignInUi {

    private static final Log log = Log.getLog(DbeaverSignInUi.class);

    private final DBRProgressMonitor monitor;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private DeviceCodeDialog dialog;

    public DbeaverSignInUi(DBRProgressMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get() || monitor.isCanceled();
    }

    @Override
    public void status(String message) {
        monitor.subTask(message);
    }

    @Override
    public void openBrowser(String url) {
        UIUtils.asyncExec(() -> {
            if (!Program.launch(url)) {
                log.error("Cannot open a web browser. Open this URL to sign in: " + url);
            }
        });
    }

    @Override
    public void showDeviceCode(DeviceCodeInfo info) {
        UIUtils.asyncExec(() -> {
            dialog = new DeviceCodeDialog(UIUtils.getActiveWorkbenchShell(), info, () -> cancelled.set(true));
            dialog.open();
        });
    }

    @Override
    public void deviceCodeFinished() {
        // Queued after showDeviceCode, so the dialog exists by the time this runs
        UIUtils.asyncExec(() -> {
            if (dialog != null) {
                dialog.close();
                dialog = null;
            }
        });
    }
}
