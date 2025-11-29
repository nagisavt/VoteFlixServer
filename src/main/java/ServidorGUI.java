import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ServidorGUI extends JFrame {

    private JTextArea logArea;
    private JTextField portField;
    private JButton btnStart;
    private JButton btnStop;
    private JLabel statusLabel;
    private DefaultListModel<String> listModel;
    private JList<String> userList;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private boolean isRunning = false;

    public ServidorGUI() {
        super("VoteFlix - Servidor Monitor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 500);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Porta:"));
        portField = new JTextField("23000", 6);
        topPanel.add(portField);

        btnStart = new JButton("Iniciar Servidor");
        btnStop = new JButton("Parar");
        btnStop.setEnabled(false);

        topPanel.add(btnStart);
        topPanel.add(btnStop);

        statusLabel = new JLabel(" Status: Parado");
        statusLabel.setForeground(Color.RED);
        topPanel.add(statusLabel);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(Color.GREEN);

        DefaultCaret caret = (DefaultCaret)logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane scrollLog = new JScrollPane(logArea);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Logs do Sistema"));
        mainPanel.add(scrollLog, BorderLayout.CENTER);

        listModel = new DefaultListModel<>();
        userList = new JList<>(listModel);
        userList.setBackground(new Color(240, 240, 240));

        JScrollPane scrollList = new JScrollPane(userList);
        scrollList.setBorder(BorderFactory.createTitledBorder("Usuários Online"));
        scrollList.setPreferredSize(new Dimension(200, 0));
        mainPanel.add(scrollList, BorderLayout.EAST);
        add(mainPanel);

        btnStart.addActionListener(e -> iniciarServidor());
        btnStop.addActionListener(e -> pararServidor());
    }

    private void iniciarServidor() {
        int porta;
        try {
            porta = Integer.parseInt(portField.getText());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Porta inválida.");
            return;
        }

        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(porta);
                atualizarStatus(true);
                log("Servidor iniciado na porta " + porta);
                log("Aguardando conexões...");

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    log("Nova conexão (Socket): " + clientSocket.getInetAddress());
                    ServidorEcho clienteHandler = new ServidorEcho(clientSocket, this);
                    clienteHandler.start();
                }

            } catch (IOException e) {
                if (isRunning) {
                    log("Erro no servidor: " + e.getMessage());
                }
            } finally {
                atualizarStatus(false);
            }
        });

        serverThread.start();
    }

    private void pararServidor() {
        try {
            isRunning = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            log("Servidor parado manualmente.");
            SwingUtilities.invokeLater(() -> listModel.clear());
        } catch (IOException e) {
            log("Erro ao fechar servidor: " + e.getMessage());
        }
    }

    private void atualizarStatus(boolean rodando) {
        this.isRunning = rodando;
        SwingUtilities.invokeLater(() -> {
            if (rodando) {
                btnStart.setEnabled(false);
                portField.setEnabled(false);
                btnStop.setEnabled(true);
                statusLabel.setText(" Status: Rodando");
                statusLabel.setForeground(new Color(0, 150, 0));
            } else {
                btnStart.setEnabled(true);
                portField.setEnabled(true);
                btnStop.setEnabled(false);
                statusLabel.setText(" Status: Parado");
                statusLabel.setForeground(Color.RED);
            }
        });
    }

    public void log(String mensagem) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + time + "] " + mensagem + "\n");
        });
    }

    public void adicionarUsuarioNaLista(String nome) {
        SwingUtilities.invokeLater(() -> {
            if (!listModel.contains(nome)) {
                listModel.addElement(nome);
            }
        });
    }

    public void removerUsuarioDaLista(String nome) {
        SwingUtilities.invokeLater(() -> {
            listModel.removeElement(nome);
        });
    }
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new ServidorGUI().setVisible(true));
    }
}