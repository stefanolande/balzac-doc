package it.unica.tcs.ui.preferences;

import java.util.concurrent.TimeUnit;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.google.common.base.Throwables;
import com.google.inject.Injector;
import com.sulacosoft.bitcoindconnector4j.BitcoindApi;

import it.unica.tcs.bitcointm.ui.internal.BitcointmActivator;
import it.unica.tcs.lib.client.BitcoinClientI;
import it.unica.tcs.lib.client.impl.RPCBitcoinClient;
import it.unica.tcs.utils.BitcoinClientFactory;
import it.unica.tcs.utils.SecureStorageUtils;

public class TrustedNodesPreferences extends PreferencePage implements IWorkbenchPreferencePage {
    private Text testnetHostText;
    private Spinner testnetPortSpinner;
    private Text testnetUsernameText;
    private Text testnetPasswordText;
    private Spinner testnetTimeoutSpinner;

    private Text mainnetHostText;
    private Spinner mainnetPortSpinner;
    private Text mainnetUsernameText;
    private Text mainnetPasswordText;
    private Spinner mainnetTimeoutSpinner;

    private ISecurePreferences secureStorage;

    private ImageDescriptor imageErrorDesc;
    private ImageDescriptor imageSuccessDesc;
    private Image imageError;
    private Image imageSuccess;

    /**
     * Create the preference page.
     */
    public TrustedNodesPreferences() {
        setDescription("Configure your trusted Bitcoin nodes to enable further static checks of your code");
        setTitle("Trusted Nodes");
        setPreferenceStore(BitcointmActivator.getInstance().getPreferenceStore());
    }

    /**
     * Initialize the preference page.
     */
    public void init(IWorkbench workbench) {
        secureStorage = SecurePreferencesFactory.getDefault();
        imageErrorDesc = BitcointmActivator.imageDescriptorFromPlugin("it.unica.tcs.bitcointm.ui", "images/error.gif");
        imageSuccessDesc = BitcointmActivator.imageDescriptorFromPlugin("it.unica.tcs.bitcointm.ui",
                "images/success.gif");
        imageError = imageErrorDesc.createImage();
        imageSuccess = imageSuccessDesc.createImage();
    }

    /**
     * Create contents of the preference page.
     * 
     * @param parent
     *            the parent composite
     */
    @Override
    public Control createContents(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new FillLayout(SWT.VERTICAL));

        // testnet node configuration
        createNetworkContents(container, true);
        // mainnet node configuration
        createNetworkContents(container, false);

        initialize(); // initialize properties values

        return container;
    }

    private void createNetworkContents(Composite parent, boolean testnet) {

        Group grpTestnet = new Group(parent, SWT.NONE);
        grpTestnet.setText(testnet ? " Testnet node " : " Mainnet node ");
        GridLayout gl_grpTestnet = new GridLayout(2, false);
        gl_grpTestnet.marginTop = 5;
        gl_grpTestnet.marginRight = 10;
        gl_grpTestnet.marginLeft = 10;
        grpTestnet.setLayout(gl_grpTestnet);

        Label testnetHostLabel = new Label(grpTestnet, SWT.NONE);
        testnetHostLabel.setText("Host");

        Text hostText = new Text(grpTestnet, SWT.BORDER);
        hostText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        Label lblPort = new Label(grpTestnet, SWT.NONE);
        lblPort.setText("Port");

        Spinner portSpinner = new Spinner(grpTestnet, SWT.BORDER);
        portSpinner.setMaximum(65535);
        portSpinner.setMinimum(1);
        GridData gd_portSpinner = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gd_portSpinner.heightHint = 19;
        gd_portSpinner.widthHint = 163;
        portSpinner.setLayoutData(gd_portSpinner);

        Label testnetUsernameLabel = new Label(grpTestnet, SWT.NONE);
        testnetUsernameLabel.setText("Username");

        Text usernameText = new Text(grpTestnet, SWT.BORDER);
        usernameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));

        Label testnetPasswordLabel = new Label(grpTestnet, SWT.NONE);
        testnetPasswordLabel.setText("Password");

        Text passwordText = new Text(grpTestnet, SWT.BORDER | SWT.PASSWORD);
        passwordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        Label testnetTimeoutLabel = new Label(grpTestnet, SWT.NONE);
        testnetTimeoutLabel.setText("Timeout");

        Spinner timeoutSpinner = new Spinner(grpTestnet, SWT.BORDER);
        timeoutSpinner.setMaximum(Integer.MAX_VALUE);
        timeoutSpinner.setMinimum(-1);
        timeoutSpinner.setToolTipText("Set -1 for undefined waiting time (not recommended)");
        GridData gd_timeoutSpinner = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gd_timeoutSpinner.heightHint = 19;
        gd_timeoutSpinner.widthHint = 163;
        timeoutSpinner.setLayoutData(gd_timeoutSpinner);

        // test button with feedbacks
        Composite compositeBtnTest = new Composite(grpTestnet, SWT.NONE);
        compositeBtnTest.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
        GridLayout gl_composite = new GridLayout(2, false);
        gl_composite.horizontalSpacing = 10;
        gl_composite.marginWidth = 0;
        compositeBtnTest.setLayout(gl_composite);
        Button btnTestConnection = new Button(compositeBtnTest, SWT.NONE);
        btnTestConnection.setText("Test");
        Composite compositeFeedbacks = new Composite(compositeBtnTest, SWT.NONE);
        Canvas testnetNetworkFeedbackOK = new Canvas(compositeFeedbacks, SWT.NONE);
        testnetNetworkFeedbackOK.setBounds(0, 0, 16, 16);
        testnetNetworkFeedbackOK.setBackgroundImage(imageSuccess);
        testnetNetworkFeedbackOK.setVisible(false);
        Canvas testneNetworkFeedbackERR = new Canvas(compositeFeedbacks, SWT.NONE);
        testneNetworkFeedbackERR.setBounds(0, 0, 16, 16);
        testneNetworkFeedbackERR.setBackgroundImage(imageError);
        testneNetworkFeedbackERR.setVisible(false);

        // error details
        StyledText testnetErrorDetailsText = new StyledText(grpTestnet,
                SWT.BORDER | SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL | SWT.CANCEL);
        GridData gd_testnetErrorDetailsText = new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1);
        gd_testnetErrorDetailsText.heightHint = -1;
        testnetErrorDetailsText.setLayoutData(gd_testnetErrorDetailsText);
        testnetErrorDetailsText.setAlwaysShowScrollBars(false);

        btnTestConnection.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseDown(MouseEvent e) {
                testnetErrorDetailsText.setText("");
                testnetNetworkFeedbackOK.setVisible(false);
                testneNetworkFeedbackERR.setVisible(false);
            }

            @Override
            public void mouseUp(MouseEvent e) {
                String address = hostText.getText();
                Integer port = portSpinner.getSelection();
                String protocol = "http";
                String user = usernameText.getText();
                String password = passwordText.getText();
                Integer timeout = timeoutSpinner.getSelection();

                if (password.isEmpty()) {
                    try {
                        if (testnet)
                            password = secureStorage
                                    .node(SecureStorageUtils.SECURE_STORAGE__NODE__BITCOIN__TESTNET_NODE)
                                    .get(SecureStorageUtils.SECURE_STORAGE__PROPERTY__TESTNET_PASSWORD, "");
                        else
                            password = secureStorage
                                    .node(SecureStorageUtils.SECURE_STORAGE__NODE__BITCOIN__MAINNET_NODE)
                                    .get(SecureStorageUtils.SECURE_STORAGE__PROPERTY__MAINNET_PASSWORD, "");

                    } catch (StorageException e1) {
                        e1.printStackTrace();
                        testnetErrorDetailsText.append("Error fetching the password from the secure store.\n\n");
                        testnetErrorDetailsText.append(Throwables.getStackTraceAsString(e1));
                        testneNetworkFeedbackERR.setVisible(true);
                        return;
                    }
                }
                RPCBitcoinClient bitcoinClient = new RPCBitcoinClient(address, port, protocol, user, password, timeout,
                        TimeUnit.MILLISECONDS);

                try {
                    BitcoindApi api = bitcoinClient.getApi();
                    boolean isTestnet = api.getinfo().getTestnet();

                    if (isTestnet != testnet) {
                        String expected = testnet ? "testnet" : "mainnet";
                        String actual = isTestnet ? "testnet" : "mainnet";
                        testnetErrorDetailsText
                                .append("Wrong network type: expected " + expected + ", found " + actual + ".");
                        testneNetworkFeedbackERR.setVisible(true);
                        return;
                    }

                } catch (Exception e1) {
                    e1.printStackTrace();
                    testnetErrorDetailsText.append("Cannot connect to the node.\n\n");
                    testnetErrorDetailsText.append(Throwables.getStackTraceAsString(e1));
                    testneNetworkFeedbackERR.setVisible(true);
                    return;
                }
                testnetErrorDetailsText.append("ok");
                testnetNetworkFeedbackOK.setVisible(true);
            }
        });

        if (testnet)
            testnetHostText = hostText;
        else
            mainnetHostText = hostText;
        if (testnet)
            testnetPortSpinner = portSpinner;
        else
            mainnetPortSpinner = portSpinner;
        if (testnet)
            testnetUsernameText = usernameText;
        else
            mainnetUsernameText = usernameText;
        if (testnet)
            testnetPasswordText = passwordText;
        else
            mainnetPasswordText = passwordText;
        if (testnet)
            testnetTimeoutSpinner = timeoutSpinner;
        else
            mainnetTimeoutSpinner = timeoutSpinner;
    }

    private void initialize() {
        IPreferenceStore store = getPreferenceStore();

        testnetHostText.setText(store.getString(PreferenceConstants.P_TESTNET_HOST));
        testnetPortSpinner.setSelection(store.getInt(PreferenceConstants.P_TESTNET_PORT));
        testnetUsernameText.setText(store.getString(PreferenceConstants.P_TESTNET_USERNAME));
        testnetTimeoutSpinner.setSelection(store.getInt(PreferenceConstants.P_TESTNET_TIMEOUT));

        if (isTestnetPasswordSaved())
            testnetPasswordText.setMessage("Not changed");
        else
            testnetPasswordText.setMessage("Enter a password");

        mainnetHostText.setText(store.getString(PreferenceConstants.P_MAINNET_HOST));
        mainnetPortSpinner.setSelection(store.getInt(PreferenceConstants.P_MAINNET_PORT));
        mainnetUsernameText.setText(store.getString(PreferenceConstants.P_MAINNET_USERNAME));
        mainnetTimeoutSpinner.setSelection(store.getInt(PreferenceConstants.P_MAINNET_TIMEOUT));

        if (isMainnetPasswordSaved())
            mainnetPasswordText.setMessage("Not changed");
        else
            mainnetPasswordText.setMessage("Enter a password");

    }

    @Override
    public void dispose() {
        imageError.dispose();
        imageSuccess.dispose();
        super.dispose();
    }

    @Override
    public void performDefaults() {
        IPreferenceStore store = getPreferenceStore();
        testnetHostText.setText(store.getDefaultString(PreferenceConstants.P_TESTNET_HOST));
        testnetPortSpinner.setSelection(store.getDefaultInt(PreferenceConstants.P_TESTNET_PORT));
        testnetUsernameText.setText(store.getDefaultString(PreferenceConstants.P_TESTNET_USERNAME));
        testnetTimeoutSpinner.setSelection(store.getDefaultInt(PreferenceConstants.P_TESTNET_TIMEOUT));
        secureStorage.node(SecureStorageUtils.SECURE_STORAGE__NODE__BITCOIN__TESTNET_NODE).removeNode();
        testnetPasswordText.setMessage("Enter a password");

        mainnetHostText.setText(store.getDefaultString(PreferenceConstants.P_MAINNET_HOST));
        mainnetPortSpinner.setSelection(store.getDefaultInt(PreferenceConstants.P_MAINNET_PORT));
        mainnetUsernameText.setText(store.getDefaultString(PreferenceConstants.P_MAINNET_USERNAME));
        mainnetTimeoutSpinner.setSelection(store.getDefaultInt(PreferenceConstants.P_MAINNET_TIMEOUT));
        secureStorage.node(SecureStorageUtils.SECURE_STORAGE__NODE__BITCOIN__MAINNET_NODE).removeNode();
        mainnetPasswordText.setMessage("Enter a password");
        super.updateApplyButton();
    }

    @Override
    public boolean performOk() {
        IPreferenceStore store = getPreferenceStore();
        store.setValue(PreferenceConstants.P_TESTNET_HOST, testnetHostText.getText());
        store.setValue(PreferenceConstants.P_TESTNET_PORT, testnetPortSpinner.getSelection());
        store.setValue(PreferenceConstants.P_TESTNET_USERNAME, testnetUsernameText.getText());
        store.setValue(PreferenceConstants.P_TESTNET_TIMEOUT, testnetTimeoutSpinner.getSelection());

        if (!testnetPasswordText.getText().isEmpty()) {
            try {
                secureStorage.node(SecureStorageUtils.SECURE_STORAGE__NODE__BITCOIN__TESTNET_NODE).put(
                        SecureStorageUtils.SECURE_STORAGE__PROPERTY__TESTNET_PASSWORD, testnetPasswordText.getText(),
                        true);
            } catch (StorageException e) {
                e.printStackTrace();
                return false;
            }
        }

        store.setValue(PreferenceConstants.P_MAINNET_HOST, mainnetHostText.getText());
        store.setValue(PreferenceConstants.P_MAINNET_PORT, mainnetPortSpinner.getSelection());
        store.setValue(PreferenceConstants.P_MAINNET_USERNAME, mainnetUsernameText.getText());
        store.setValue(PreferenceConstants.P_MAINNET_TIMEOUT, mainnetTimeoutSpinner.getSelection());

        if (!mainnetPasswordText.getText().isEmpty()) {
            try {
                secureStorage.node(SecureStorageUtils.SECURE_STORAGE__NODE__BITCOIN__MAINNET_NODE).put(
                        SecureStorageUtils.SECURE_STORAGE__PROPERTY__MAINNET_PASSWORD, mainnetPasswordText.getText(),
                        true);
            } catch (StorageException e) {
                e.printStackTrace();
                return false;
            }
        }

        try {
            setBitcoinClientFactoryNodes(store);
        } catch (StorageException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static void setBitcoinClientFactoryNodes(IPreferenceStore store) throws StorageException {
        ISecurePreferences secureStore = SecurePreferencesFactory.getDefault();

        String testnetHost = store.getString(PreferenceConstants.P_TESTNET_HOST);
        int testnetPort = store.getInt(PreferenceConstants.P_TESTNET_PORT);
        String testnetUsername = store.getString(PreferenceConstants.P_TESTNET_USERNAME);
        int testnetTimeout = store.getInt(PreferenceConstants.P_TESTNET_TIMEOUT);
        String testnetPassword = secureStore.node(SecureStorageUtils.SECURE_STORAGE__NODE__BITCOIN__TESTNET_NODE)
                .get(SecureStorageUtils.SECURE_STORAGE__PROPERTY__TESTNET_PASSWORD, "");

        String mainnetHost = store.getString(PreferenceConstants.P_MAINNET_HOST);
        int mainnetPort = store.getInt(PreferenceConstants.P_MAINNET_PORT);
        String mainnetUsername = store.getString(PreferenceConstants.P_MAINNET_USERNAME);
        int mainnetTimeout = store.getInt(PreferenceConstants.P_MAINNET_TIMEOUT);
        String mainnetPassword = secureStore.node(SecureStorageUtils.SECURE_STORAGE__NODE__BITCOIN__MAINNET_NODE)
                .get(SecureStorageUtils.SECURE_STORAGE__PROPERTY__MAINNET_PASSWORD, "");

        BitcoinClientI testnetClient = new RPCBitcoinClient(testnetHost, testnetPort, "http", testnetUsername,
                testnetPassword, testnetTimeout, TimeUnit.MILLISECONDS);
        BitcoinClientI mainnetClient = new RPCBitcoinClient(mainnetHost, mainnetPort, "http", mainnetUsername,
                mainnetPassword, mainnetTimeout, TimeUnit.MILLISECONDS);

        Injector injector = BitcointmActivator.getInstance().getInjector(BitcointmActivator.IT_UNICA_TCS_BITCOINTM);
        BitcoinClientFactory factory = injector.getInstance(BitcoinClientFactory.class);
        factory.setMainnetClient(mainnetClient);
        factory.setTestnetClient(testnetClient);
    }

    private boolean isTestnetPasswordSaved() {
        return secureStorage.nodeExists(SecureStorageUtils.SECURE_STORAGE__NODE__BITCOIN__TESTNET_NODE);
    }

    private boolean isMainnetPasswordSaved() {
        return secureStorage.nodeExists(SecureStorageUtils.SECURE_STORAGE__NODE__BITCOIN__MAINNET_NODE);
    }

}
