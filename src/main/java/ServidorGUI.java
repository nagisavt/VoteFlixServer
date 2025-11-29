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

    private ServerSocket serverSocket;
    private Thread serverThread;
    private boolean isRunning = false;

    public ServidorGUI() {
        super("VoteFlix - Servidor Monitor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 450);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());

        // --- Painel Superior (Configuração) ---
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

        // --- Área de Logs Central ---
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(Color.GREEN);

        // Auto-scroll para a última linha
        DefaultCaret caret = (DefaultCaret)logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Logs do Sistema"));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        add(mainPanel);

        // --- Ações dos Botões ---
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

        // Inicia a thread do servidor para não travar a GUI
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(porta);
                atualizarStatus(true);
                log("Servidor iniciado na porta " + porta);
                log("Aguardando conexões...");

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    log("Nova conexão recebida: " + clientSocket.getInetAddress());

                    // AQUI NÓS CHAMAMOS SUA CLASSE ANTIGA, MAS PASSANDO O LOG
                    // Instancia a thread do cliente e inicia
                    ServidorEcho clienteHandler = new ServidorEcho(clientSocket, this::log);
                    clienteHandler.start();
                }

            } catch (IOException e) {
                if (isRunning) { // Se não foi um stop manual
                    log("Erro no servidor: " + e.getMessage());
                    e.printStackTrace();
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
                statusLabel.setForeground(new Color(0, 150, 0)); // Verde escuro
            } else {
                btnStart.setEnabled(true);
                portField.setEnabled(true);
                btnStop.setEnabled(false);
                statusLabel.setText(" Status: Parado");
                statusLabel.setForeground(Color.RED);
            }
        });
    }

    // Método público para ser chamado por outras classes (thread-safe)
    public void log(String mensagem) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + time + "] " + mensagem + "\n");
        });
    }

    // Interface funcional para passar o método de log
    public interface Logger {
        void log(String msg);
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new ServidorGUI().setVisible(true));
    }
}