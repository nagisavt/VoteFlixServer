import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class ClienteGUI extends JFrame {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final Gson gson = new Gson();
    private String tokenLogado = null;
    private String usuarioLogadoNome = null;

    private CardLayout cardLayout;
    private JPanel mainPanel;
    private JTextField ipField, portField, userField;
    private JPasswordField passField;
    private JTable moviesTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;

    public ClienteGUI() {
        super("VoteFlix - Cliente Gráfico");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        mainPanel.add(criarPainelConexao(), "CONEXAO");
        mainPanel.add(criarPainelLogin(), "LOGIN");
        mainPanel.add(criarPainelPrincipal(), "APP");

        add(mainPanel);

        statusLabel = new JLabel(" Desconectado");
        statusLabel.setBorder(BorderFactory.createEtchedBorder());
        add(statusLabel, BorderLayout.SOUTH);

        System.out.println(">>> Cliente GUI Iniciado <<<");
    }

    private JPanel criarPainelConexao() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        ipField = new JTextField("localhost", 15);
        portField = new JTextField("23000", 5);
        JButton btnConnect = new JButton("Conectar");

        panel.add(new JLabel("IP:"), gbc);
        panel.add(ipField, gbc);
        panel.add(new JLabel("Porta:"), gbc);
        panel.add(portField, gbc);
        gbc.gridy = 1; gbc.gridwidth = 4;
        panel.add(btnConnect, gbc);

        btnConnect.addActionListener(e -> conectar());
        return panel;
    }

    private JPanel criarPainelLogin() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        userField = new JTextField(15);
        passField = new JPasswordField(15);
        JButton btnLogin = new JButton("Login");
        JButton btnReg = new JButton("Registrar");

        gbc.gridx=0; gbc.gridy=0; panel.add(new JLabel("Usuario:"), gbc);
        gbc.gridx=1; panel.add(userField, gbc);
        gbc.gridx=0; gbc.gridy=1; panel.add(new JLabel("Senha:"), gbc);
        gbc.gridx=1; panel.add(passField, gbc);
        gbc.gridx=0; gbc.gridy=2; gbc.gridwidth=2;
        JPanel bp = new JPanel(); bp.add(btnLogin); bp.add(btnReg);
        panel.add(bp, gbc);

        btnLogin.addActionListener(e -> login());
        btnReg.addActionListener(e -> registrar());
        return panel;
    }

    private JPanel criarPainelPrincipal() {
        JPanel panel = new JPanel(new BorderLayout());
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton btnRefresh = new JButton("Atualizar");
        JButton btnDetails = new JButton("Detalhes");
        JButton btnAvaliar = new JButton("Avaliar Filme");
        JButton btnMyReviews = new JButton("Minhas Reviews");
        JButton btnEditReview = new JButton("Editar Review (ID)");
        JButton btnDelReview = new JButton("Excluir Review (ID)");
        JButton btnAdminMenu = new JButton("Menu Admin ▼");
        JButton btnLogout = new JButton("Sair");
        JButton btnPerfil = new JButton("Perfil");

        toolBar.add(btnRefresh);
        toolBar.add(btnDetails);
        toolBar.add(btnAvaliar);
        toolBar.add(btnMyReviews);
        toolBar.add(btnEditReview);
        toolBar.add(btnDelReview);
        toolBar.addSeparator();
        toolBar.add(btnAdminMenu);
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add(btnPerfil);
        toolBar.add(btnLogout);

        panel.add(toolBar, BorderLayout.NORTH);

        String[] colunas = {"ID", "Título", "Ano", "Diretor", "Nota Média", "Votos"};
        tableModel = new DefaultTableModel(colunas, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        moviesTable = new JTable(tableModel);
        panel.add(new JScrollPane(moviesTable), BorderLayout.CENTER);
        btnRefresh.addActionListener(e -> carregarFilmes());
        btnDetails.addActionListener(e -> verDetalhes());
        btnAvaliar.addActionListener(e -> avaliarFilme());
        btnMyReviews.addActionListener(e -> listarMinhasReviews());
        btnEditReview.addActionListener(e -> editarMinhaReview());
        btnDelReview.addActionListener(e -> excluirMinhaReview());

        btnAdminMenu.addActionListener(e -> exibirMenuAdmin(btnAdminMenu));
        btnLogout.addActionListener(e -> logout());
        btnPerfil.addActionListener(e -> verPerfil());

        return panel;
    }

    private void exibirMenuAdmin(JButton invoker) {
        JPopupMenu adminMenu = new JPopupMenu();

        JMenuItem itemAdd = new JMenuItem("Adicionar Filme");
        JMenuItem itemEdit = new JMenuItem("Editar Filme (ID)");
        JMenuItem itemDel = new JMenuItem("Excluir Filme");
        JMenuItem itemListUsers = new JMenuItem("Listar Usuários");
        JMenuItem itemEditUser = new JMenuItem("Editar Usuário (ID)");
        JMenuItem itemDelUser = new JMenuItem("Excluir Usuário (ID)");

        itemAdd.addActionListener(e -> addFilme());
        itemEdit.addActionListener(e -> editarFilmeAdmin());
        itemDel.addActionListener(e -> delFilme());
        itemListUsers.addActionListener(e -> listarUsuariosAdmin());
        itemEditUser.addActionListener(e -> editarUsuarioAdmin());
        itemDelUser.addActionListener(e -> excluirUsuarioAdmin());

        adminMenu.add(itemAdd);
        adminMenu.add(itemEdit);
        adminMenu.add(itemDel);
        adminMenu.addSeparator();
        adminMenu.add(itemListUsers);
        adminMenu.add(itemEditUser);
        adminMenu.add(itemDelUser);

        adminMenu.show(invoker, 0, invoker.getHeight());
    }

    private void conectar() {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                socket = new Socket(ipField.getText(), Integer.parseInt(portField.getText()));
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                return null;
            }
            protected void done() {
                try {
                    get();
                    cardLayout.show(mainPanel, "LOGIN");
                    statusLabel.setText(" Conectado");
                }
                catch (Exception e) {
                    JOptionPane.showMessageDialog(ClienteGUI.this, "Erro conexão: " + e);
                }
            }
        }.execute();
    }

    private void enviar(JsonObject req, ResponseHandler h) {
        if(tokenLogado != null && !req.has("token")) {
            req.addProperty("token", tokenLogado);
        }

        String jsonParaEnviar = gson.toJson(req);
        System.out.println("\nCliente enviou: " + jsonParaEnviar);

        new SwingWorker<JsonObject, Void>() {
            protected JsonObject doInBackground() throws Exception {
                out.println(jsonParaEnviar);
                String r = in.readLine();
                return r == null ? null : gson.fromJson(r, JsonObject.class);
            }
            protected void done() {
                try {
                    JsonObject res = get();
                    if(res!=null) {
                        System.out.println("Servidor respondeu: " + gson.toJson(res));

                        String status = "???";
                        String msg = "Servidor não enviou mensagem.";

                        if (res.has("status") && res.get("status").isJsonPrimitive()) {
                            status = res.get("status").getAsString();
                        }
                        if (res.has("mensagem") && res.get("mensagem").isJsonPrimitive()) {
                            msg = res.get("mensagem").getAsString();
                        }

                        if (!status.equals("200") && !status.equals("201")) {
                            System.out.println("  >>> (Status " + status + ") " + msg);
                        }

                        if (status.equals("401")) {
                            System.out.println("  >>> Seu token expirou ou é invalido. Voce foi desconectado.");
                            logout();
                        }
                        h.handle(res);

                    } else {
                        System.out.println(">>> Conexão perdida (res == null)");
                        JOptionPane.showMessageDialog(ClienteGUI.this, "Conexão perdida");
                    }
                } catch (Exception e) {
                    System.out.println("  >>> ERRO INESPERADO no cliente: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    private void login() {
        JsonObject req = new JsonObject(); req.addProperty("operacao", "LOGIN");
        req.addProperty("usuario", userField.getText()); req.addProperty("senha", new String(passField.getPassword()));

        enviar(req, res -> {
            String status = res.get("status").getAsString();
            if(status.equals("200")) {
                tokenLogado = res.get("token").getAsString();
                usuarioLogadoNome = userField.getText();
                System.out.println("  >>> (Status 200) Login realizado com sucesso.");
                cardLayout.show(mainPanel, "APP");
                statusLabel.setText(" Logado: " + usuarioLogadoNome);
                carregarFilmes();
            } else {
                erro(res);
            }
        });
    }

    private void registrar() {
        JsonObject u = new JsonObject(); u.addProperty("nome", userField.getText()); u.addProperty("senha", new String(passField.getPassword()));
        JsonObject req = new JsonObject(); req.addProperty("operacao", "CRIAR_USUARIO"); req.add("usuario", u);

        enviar(req, res -> {
            if(res.get("status").getAsString().equals("201")) {
                System.out.println("  >>> (Status 201) Usuario criado com sucesso. Voce ja pode fazer login.");
                JOptionPane.showMessageDialog(this,"Usuário criado!");
            } else {
                erro(res);
            }
        });
    }

    private void carregarFilmes() {
        JsonObject req = new JsonObject(); req.addProperty("operacao", "LISTAR_FILMES");
        enviar(req, res -> {
            if(res.get("status").getAsString().equals("200")) {
                tableModel.setRowCount(0);
                for(var e : res.getAsJsonArray("filmes")) {
                    JsonObject f = e.getAsJsonObject();
                    tableModel.addRow(new Object[]{
                            f.get("id").getAsString(), f.get("titulo").getAsString(), f.get("ano").getAsString(),
                            f.get("diretor").getAsString(), f.get("nota").getAsString(), f.get("qtd_avaliacoes").getAsString()
                    });
                }
            } else erro(res);
        });
    }

    private void verDetalhes() {
        String id = getId(); if(id==null) return;
        JsonObject req = new JsonObject(); req.addProperty("operacao", "BUSCAR_FILME_ID"); req.addProperty("id_filme", id);
        enviar(req, res -> {
            if(res.get("status").getAsString().equals("200")) {
                JsonObject f = res.getAsJsonObject("filme");
                StringBuilder sb = new StringBuilder();
                sb.append("TÍTULO: ").append(f.get("titulo").getAsString()).append("\n");
                sb.append("SINOPSE: ").append(f.get("sinopse").getAsString()).append("\n\n");

                sb.append("=== REVIEWS ===\n");
                JsonArray reviews = res.getAsJsonArray("reviews");
                if (reviews.size() == 0) sb.append("(Nenhuma avaliação ainda)\n");

                for(var r : reviews) {
                    JsonObject rev = r.getAsJsonObject();
                    sb.append("--------------------------------------------------\n");
                    sb.append("REVIEW ID: ").append(rev.get("id").getAsString()).append("\n");
                    sb.append("Título: ").append(rev.get("titulo").getAsString());
                    sb.append(" (por ").append(rev.get("nome_usuario").getAsString()).append(")\n");
                    sb.append("Nota: ").append(rev.get("nota").getAsString()).append("/5");

                    if (rev.has("data")) {
                        sb.append("  |  Data: ").append(rev.get("data").getAsString());
                    }

                    if (rev.has("editado") && rev.get("editado").getAsString().equals("true")) {
                        sb.append(" (Editado)");
                    }
                    sb.append("\n\n");
                    sb.append("Resenha:\n\"");
                    sb.append(rev.get("descricao").getAsString());
                    sb.append("\"\n");
                }

                JTextArea ta = new JTextArea(sb.toString(), 20, 50);
                ta.setEditable(false);
                ta.setLineWrap(true);
                ta.setWrapStyleWord(true);
                JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Detalhes do Filme", JOptionPane.INFORMATION_MESSAGE);
            } else erro(res);
        });
    }

    private void avaliarFilme() {
        String id = getId(); if(id==null) return;

        JTextField titField = new JTextField();
        JTextArea descField = new JTextArea(3, 20);
        JComboBox<String> notaBox = new JComboBox<>(new String[]{"5","4","3","2","1"});

        Object[] msg = {"Título:", titField, "Nota:", notaBox, "Comentário:", new JScrollPane(descField)};
        if(JOptionPane.showConfirmDialog(this, msg, "Avaliar", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            JsonObject rev = new JsonObject();
            rev.addProperty("id_filme", id);
            rev.addProperty("titulo", titField.getText());
            rev.addProperty("descricao", descField.getText());
            rev.addProperty("nota", (String)notaBox.getSelectedItem());

            JsonObject req = new JsonObject(); req.addProperty("operacao", "CRIAR_REVIEW"); req.add("review", rev);
            enviar(req, res -> {
                if(res.get("status").getAsString().equals("201")) {
                    JOptionPane.showMessageDialog(this, "Avaliação enviada!");
                    carregarFilmes();
                } else erro(res);
            });
        }
    }

    private void listarMinhasReviews() {
        JsonObject req = new JsonObject();
        req.addProperty("operacao", "LISTAR_REVIEWS_USUARIO");

        enviar(req, res -> {
            if (res.get("status").getAsString().equals("200")) {
                JsonArray reviews = res.getAsJsonArray("reviews");
                StringBuilder sb = new StringBuilder();
                if (reviews.size() == 0) {
                    sb.append("(Você ainda não fez nenhuma avaliação)");
                } else {
                    for (var r : reviews) {
                        JsonObject rev = r.getAsJsonObject();
                        sb.append("REVIEW ID: ").append(rev.get("id").getAsString()).append("\n");
                        sb.append("Filme ID: ").append(rev.get("id_filme").getAsString()).append("\n");
                        sb.append("Nota: ").append(rev.get("nota").getAsString()).append("\n");
                        sb.append("Título: ").append(rev.get("titulo").getAsString()).append("\n");
                        sb.append("Data: ").append(rev.get("data").getAsString());
                        if (rev.get("editado").getAsString().equals("true")) sb.append(" (Editado)");
                        sb.append("\nDesc: ").append(rev.get("descricao").getAsString()).append("\n");
                        sb.append("--------------------------------------------------\n");
                    }
                }
                JTextArea ta = new JTextArea(sb.toString(), 20, 40);
                ta.setEditable(false);
                ta.setLineWrap(true);
                ta.setWrapStyleWord(true);
                JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Minhas Reviews", JOptionPane.INFORMATION_MESSAGE);
            } else {
                erro(res);
            }
        });
    }

    private void excluirMinhaReview() {
        String idReview = JOptionPane.showInputDialog(this, "Digite o ID da review que deseja excluir:");
        if (idReview == null || idReview.trim().isEmpty()) return;

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "EXCLUIR_REVIEW");
        req.addProperty("id", idReview);

        enviar(req, res -> {
            if (res.get("status").getAsString().equals("200")) {
                JOptionPane.showMessageDialog(this, "Review excluída.");
                carregarFilmes();
            } else {
                erro(res);
            }
        });
    }

    private void editarMinhaReview() {
        String id = JOptionPane.showInputDialog(this, "ID da review a editar:");
        if(id == null || id.trim().isEmpty()) return;

        JTextField tit = new JTextField();
        JTextArea desc = new JTextArea(3,20);
        JComboBox<String> nota = new JComboBox<>(new String[]{"5","4","3","2","1"});

        Object[] msg = {"Novo Título:", tit, "Nova Nota:", nota, "Nova Descrição:", new JScrollPane(desc)};

        if(JOptionPane.showConfirmDialog(this, msg, "Editar Review", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            JsonObject rev = new JsonObject();

            rev.addProperty("id", id);

            rev.addProperty("titulo", tit.getText());
            rev.addProperty("descricao", desc.getText());
            rev.addProperty("nota", (String)nota.getSelectedItem());

            JsonObject req = new JsonObject();
            req.addProperty("operacao", "EDITAR_REVIEW");
            req.add("review", rev);

            enviar(req, res -> {
                if(res.get("status").getAsString().equals("200")) {
                    JOptionPane.showMessageDialog(this, "Review editada com sucesso!");
                } else {
                    erro(res);
                }
            });
        }
    }

    private void addFilme() {
        JTextField t = new JTextField();
        JTextField d = new JTextField();
        JTextField a = new JTextField();
        JTextField g = new JTextField();
        JTextArea s = new JTextArea(5, 20);

        Object[] message = {
                "Título:", t, "Diretor:", d, "Ano:", a,
                "Gêneros (sep. virgula):", g, "Sinopse:", new JScrollPane(s)
        };

        if(JOptionPane.showConfirmDialog(this, message, "Novo Filme", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            JsonObject f = new JsonObject();
            f.addProperty("titulo", t.getText());
            f.addProperty("diretor", d.getText());
            f.addProperty("ano", a.getText());
            f.addProperty("sinopse", s.getText());

            JsonArray ga = new JsonArray();
            for(String str : g.getText().split(",")) ga.add(str.trim());
            f.add("genero", ga);

            JsonObject req = new JsonObject(); req.addProperty("operacao", "CRIAR_FILME"); req.add("filme", f);

            enviar(req, res -> {
                if(res.get("status").getAsString().equals("201")) {
                    System.out.println("  >>> (Status 201) Filme criado com sucesso.");
                    carregarFilmes();
                } else {
                    erro(res);
                }
            });
        }
    }

    private void editarFilmeAdmin() {
        String id = JOptionPane.showInputDialog(this, "ID do filme a editar:");
        if(id == null || id.trim().isEmpty()) return;

        JTextField t=new JTextField(), d=new JTextField(), a=new JTextField(), g=new JTextField();
        JTextArea s=new JTextArea(5,20);
        Object[] message = {"Novo Título:", t, "Novo Diretor:", d, "Novo Ano:", a, "Novos Gêneros:", g, "Nova Sinopse:", new JScrollPane(s)};

        if(JOptionPane.showConfirmDialog(this, message, "Editar Filme", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            JsonObject f = new JsonObject();
            f.addProperty("id", id);
            f.addProperty("titulo", t.getText());
            f.addProperty("diretor", d.getText());
            f.addProperty("ano", a.getText());
            f.addProperty("sinopse", s.getText());
            JsonArray ga = new JsonArray(); for(String str:g.getText().split(",")) ga.add(str.trim());
            f.add("genero", ga);

            JsonObject req = new JsonObject();
            req.addProperty("operacao", "EDITAR_FILME");
            req.add("filme", f);

            enviar(req, res -> {
                if(res.get("status").getAsString().equals("200")) {
                    JOptionPane.showMessageDialog(this, "Filme editado!");
                    carregarFilmes();
                } else erro(res);
            });
        }
    }

    private void delFilme() {
        String id = getId(); if(id==null) return;
        if(JOptionPane.showConfirmDialog(this, "Apagar filme " + id + "?") == JOptionPane.YES_OPTION) {
            JsonObject req = new JsonObject(); req.addProperty("operacao", "EXCLUIR_FILME"); req.addProperty("id", id);
            enviar(req, res -> {
                if(res.get("status").getAsString().equals("200")) {
                    System.out.println("  >>> (Status 200) Filme excluido com sucesso.");
                    carregarFilmes();
                } else {
                    erro(res);
                }
            });
        }
    }

    private void listarUsuariosAdmin() {
        JsonObject req = new JsonObject();
        req.addProperty("operacao", "LISTAR_USUARIOS");

        enviar(req, res -> {
            if(res.get("status").getAsString().equals("200")) {
                JsonArray usuarios = res.getAsJsonArray("usuarios");
                StringBuilder sb = new StringBuilder("--- Lista de Usuários ---\n\n");

                for(var u : usuarios) {
                    JsonObject user = u.getAsJsonObject();
                    sb.append("ID: ").append(user.get("id").getAsString())
                            .append(" | Nome: ").append(user.get("nome").getAsString()).append("\n");
                }

                JTextArea ta = new JTextArea(sb.toString(), 15, 30);
                ta.setEditable(false);
                JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Usuários (Admin)", JOptionPane.INFORMATION_MESSAGE);

            } else {
                erro(res);
            }
        });
    }

    private void editarUsuarioAdmin() {
        String idAlvo = JOptionPane.showInputDialog(this, "ID do usuário a editar:");
        if (idAlvo == null || idAlvo.trim().isEmpty()) return;

        String novaSenha = JOptionPane.showInputDialog(this, "Nova senha para o usuário ID " + idAlvo + ":");
        if (novaSenha == null || novaSenha.trim().isEmpty()) return;

        JsonObject usuarioObj = new JsonObject();
        usuarioObj.addProperty("senha", novaSenha);

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "ADMIN_EDITAR_USUARIO");
        req.addProperty("id", idAlvo);
        req.add("usuario", usuarioObj);

        enviar(req, res -> {
            if (res.get("status").getAsString().equals("200")) {
                System.out.println("  >>> (Status 200) Sucesso: operação realizada com sucesso");
                JOptionPane.showMessageDialog(this, "Senha atualizada com sucesso.");
            } else {
                erro(res);
            }
        });
    }

    private void excluirUsuarioAdmin() {
        String idAlvo = JOptionPane.showInputDialog(this, "ID do usuário a EXCLUIR:");
        if (idAlvo == null || idAlvo.trim().isEmpty()) return;

        if (JOptionPane.showConfirmDialog(this, "Tem certeza que deseja apagar o usuário ID " + idAlvo + "?", "Cuidado", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "ADMIN_EXCLUIR_USUARIO");
        req.addProperty("id", idAlvo);

        enviar(req, res -> {
            if (res.get("status").getAsString().equals("200")) {
                System.out.println("  >>> (Status 200) Sucesso: operação realizada com sucesso");
                JOptionPane.showMessageDialog(this, "Usuário excluído.");
            } else {
                erro(res);
            }
        });
    }

    private void verPerfil() {
        JsonObject req = new JsonObject();
        req.addProperty("operacao", "LISTAR_PROPRIO_USUARIO");
        enviar(req, res -> {
            if(res.get("status").getAsString().equals("200")) {
                String nome = res.get("usuario").getAsString();
                System.out.println("  >>> (Status 200) Usuario logado: " + nome);

                String[] options = {"Voltar", "Alterar Senha", "Excluir Minha Conta"};
                int choice = JOptionPane.showOptionDialog(this,
                        "Usuário: " + nome + "\nO que deseja fazer?",
                        "Meu Perfil",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);

                if (choice == 1) alterarSenha();
                if (choice == 2) excluirConta();
            } else erro(res);
        });
    }

    private void alterarSenha() {
        String novaSenha = JOptionPane.showInputDialog(this, "Digite sua nova senha:");
        if (novaSenha == null || novaSenha.trim().isEmpty()) return;

        JsonObject usuarioObj = new JsonObject();
        usuarioObj.addProperty("senha", novaSenha);

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "EDITAR_PROPRIO_USUARIO");
        req.add("usuario", usuarioObj);

        enviar(req, res -> {
            if (res.get("status").getAsString().equals("200")) {
                JOptionPane.showMessageDialog(this, "Senha alterada com sucesso.");
                System.out.println("  >>> (Status 200) Senha alterada com sucesso.");
            } else {
                erro(res);
            }
        });
    }

    private void excluirConta() {
        if (JOptionPane.showConfirmDialog(this, "Tem certeza absoluta?", "Cuidado", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            JsonObject req = new JsonObject(); req.addProperty("operacao", "EXCLUIR_PROPRIO_USUARIO");
            enviar(req, res -> {
                if (res.get("status").getAsString().equals("200")) {
                    System.out.println("  >>> (Status 200) Usuario excluido com sucesso. Desconectando...");
                    JOptionPane.showMessageDialog(this, "Excluído."); logout();
                } else {
                    erro(res);
                }
            });
        }
    }

    private void logout() {
        if(tokenLogado != null) {
            JsonObject req = new JsonObject(); req.addProperty("operacao", "LOGOUT");
            enviar(req, res -> {
                System.out.println("  >>> (Status 200) Logout realizado com sucesso. Desconectando...");
            });
        }
        tokenLogado = null;
        cardLayout.show(mainPanel, "LOGIN");
        statusLabel.setText(" Desconectado");
    }

    private String getId() {
        int row = moviesTable.getSelectedRow();
        if(row == -1) { JOptionPane.showMessageDialog(this, "Selecione um filme."); return null; }
        return (String) tableModel.getValueAt(row, 0);
    }

    private void erro(JsonObject res) {
        String msg = res.has("mensagem")?res.get("mensagem").getAsString():"Desconhecido";
        JOptionPane.showMessageDialog(this, "Erro: " + msg);
    }

    interface ResponseHandler { void handle(JsonObject res); }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClienteGUI().setVisible(true));
    }
}