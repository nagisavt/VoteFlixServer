import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;

public class ServidorEcho extends Thread {

    protected Socket socketCliente;
    private static final String urlBancoDados = "jdbc:sqlite:voteflix.db";

    private static final String CHAVE_SECRETA_STRING = "QK55qT2jmZAkPHABB1acTQmtLyObqb6E";
    private static final SecretKey CHAVE_SECRETA = Keys.hmacShaKeyFor(CHAVE_SECRETA_STRING.getBytes());
    private static final long EXPIRACAO_TOKEN_MINUTOS = 60L;

    private static void inicializarBancoDeDados() {
        String sqlFilmes = "CREATE TABLE IF NOT EXISTS filmes (id INTEGER PRIMARY KEY AUTOINCREMENT, titulo TEXT NOT NULL, diretor TEXT, ano TEXT, genero TEXT, sinopse TEXT)";
        String sqlUsuarios = "CREATE TABLE IF NOT EXISTS usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL UNIQUE, senha TEXT NOT NULL, is_admin INTEGER DEFAULT 0)";
        String sqlReviews = "CREATE TABLE IF NOT EXISTS reviews (id INTEGER PRIMARY KEY AUTOINCREMENT, id_filme INTEGER NOT NULL, titulo TEXT, descricao TEXT, nota TEXT, id_usuario INTEGER, data TEXT, FOREIGN KEY(id_filme) REFERENCES filmes(id), FOREIGN KEY(id_usuario) REFERENCES usuarios(id))";
        String sqlAdmin = "INSERT OR IGNORE INTO usuarios (nome, senha, is_admin) VALUES ('admin', 'admin', 1)";

        try (Connection conexao = DriverManager.getConnection(urlBancoDados);
             Statement comando = conexao.createStatement()) {
            comando.execute(sqlFilmes);
            comando.execute(sqlUsuarios);
            comando.execute(sqlReviews);
            comando.execute(sqlAdmin);
            System.out.println("Banco de dados inicializado.");
        } catch (SQLException e) {
            System.err.println("Erro ao inicializar banco de dados: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        ServerSocket servidorSocket = null;
        inicializarBancoDeDados();
        System.out.println("Qual porta o servidor VoteFlix deve usar? (ex: 23000) ");
        int porta = 23000;

        try (BufferedReader leitorConsole = new BufferedReader(new InputStreamReader(System.in))) {
            porta = Integer.parseInt(leitorConsole.readLine());
        } catch (IOException | NumberFormatException e) {
            System.err.println("Entrada invalida, usando porta padrao 23000. Erro: " + e.getMessage());
        }

        System.out.println("Servidor VoteFlix carregado na porta " + porta);
        System.out.println("Aguardando conexoes....\n ");
        try {
            servidorSocket = new ServerSocket(porta);
            System.out.println("Socket de Conexao criado.\n");
            while (true) {
                try {
                    Socket novoSocketCliente = servidorSocket.accept();
                    System.out.println("Nova conexao recebida. Criando thread...");
                    new ServidorEcho(novoSocketCliente);
                } catch (IOException e) {
                    System.err.println("Erro ao aceitar conexao cliente: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Nao foi possivel ouvir a porta " + porta + " " + e.getMessage());
            System.exit(1);
        } finally {
            if (servidorSocket != null && !servidorSocket.isClosed()) {
                try {
                    servidorSocket.close();
                } catch (IOException e) {
                    System.err.println("Erro ao fechar server socket: " + e.getMessage());
                }
            }
        }
    }

    private ServidorEcho(Socket socketCliente) {
        this.socketCliente = socketCliente;
        this.start();
    }

    private Claims validarTokenExtrairDados(String token) {
        if (token == null || token.isEmpty()) {
            System.err.println("Tentativa de validacao com token nulo ou vazio.");
            return null;
        }
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(CHAVE_SECRETA)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            System.err.println("Token expirado: " + e.getMessage());
            return null;
        } catch (MalformedJwtException | SignatureException | IllegalArgumentException e) {
            System.err.println("Token invalido ou mal formatado: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("Erro inesperado ao validar token: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void run() {
        System.out.println("Nova thread de comunicacao iniciada.");

        try (PrintWriter saida = new PrintWriter(this.socketCliente.getOutputStream(), true);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(this.socketCliente.getInputStream()))) {

            Gson gson = new Gson();
            String linhaEntrada;
            while ((linhaEntrada = entrada.readLine()) != null) {

                System.out.println("Cliente enviou: " + linhaEntrada);
                String respostaJson = "";
                JsonObject requisicao = null;
                String operacao = null;

                try {
                    try {
                        requisicao = gson.fromJson(linhaEntrada, JsonObject.class);
                    } catch (JsonSyntaxException jsonEx) {
                        System.err.println("Erro de sintaxe JSON: " + jsonEx.getMessage());
                        respostaJson = "{\"status\": \"400\", \"mensagem\": \"Erro: Operação não encontrada ou inválida (Json do cliente invalido)\"}";
                        System.out.println("Servidor respondeu: " + respostaJson);
                        saida.println(respostaJson);
                        continue;
                    }

                    if (requisicao == null || !requisicao.has("operacao") || !requisicao.get("operacao").isJsonPrimitive()) {
                        respostaJson = "{\"status\": \"400\", \"mensagem\": \"Erro: Operação não encontrada ou inválida\"}";
                        System.out.println("Servidor respondeu: " + respostaJson);
                        saida.println(respostaJson);
                        continue;
                    }

                    operacao = requisicao.get("operacao").getAsString();

                    if (operacao.equals("LOGIN")) {
                        String usuario = null;
                        String senha = null;
                        try {
                            usuario = requisicao.get("usuario").getAsString();
                            senha = requisicao.get("senha").getAsString();

                            String sql = "SELECT id, is_admin FROM usuarios WHERE nome = ? AND senha = ?";
                            try (Connection conexao = DriverManager.getConnection(urlBancoDados);
                                 PreparedStatement comandoSql = conexao.prepareStatement(sql)) {
                                comandoSql.setString(1, usuario);
                                comandoSql.setString(2, senha);
                                try (ResultSet resultado = comandoSql.executeQuery()) {
                                    if (resultado.next()) {
                                        int idUsuario = resultado.getInt("id");
                                        boolean ehAdminBanco = resultado.getInt("is_admin") == 1;
                                        Date agora = new Date();
                                        Date dataExpiracao = new Date(agora.getTime() + TimeUnit.MINUTES.toMillis(EXPIRACAO_TOKEN_MINUTOS));
                                        String tokenJwt = Jwts.builder()
                                                .setSubject(String.valueOf(idUsuario))
                                                .claim("usuario", usuario)
                                                .claim("funcao", ehAdminBanco ? "admin" : "user")
                                                .setIssuedAt(agora)
                                                .setExpiration(dataExpiracao)
                                                .signWith(CHAVE_SECRETA, SignatureAlgorithm.HS256)
                                                .compact();
                                        respostaJson = String.format("{\"status\": \"200\", \"mensagem\": \"Sucesso: operação realizada com sucesso\", \"token\": \"%s\"}", tokenJwt);
                                    } else {
                                        respostaJson = "{\"status\": \"400\", \"mensagem\": \"Erro: Credenciais inválidas\"}";
                                    }
                                }
                            } catch (SQLException e) {
                                System.err.println("Erro SQL no LOGIN: " + e.getMessage());
                                respostaJson = "{\"status\": \"500\", \"mensagem\": \"Erro: Falha interna do servidor\"}";
                            }
                        } catch (Exception e) {
                            System.err.println("Erro ao obter 'usuario' ou 'senha' do JSON: " + e.getMessage());
                            respostaJson = "{\"status\": \"422\", \"mensagem\": \"Erro: Chaves faltantes ou invalidas\"}";
                        }
                    } else if (operacao.equals("CRIAR_USUARIO")) {
                        try {
                            JsonObject usuarioJson = requisicao.get("usuario").getAsJsonObject();
                            String nome = usuarioJson.get("nome").getAsString();
                            String senha = usuarioJson.get("senha").getAsString();

                            if (nome.isEmpty() || senha.isEmpty() || nome.length() > 50 || senha.length() < 3) {
                                respostaJson = "{\"status\": \"405\", \"mensagem\": \"Erro: Campos inválidos, verifique o tipo e quantidade de caracteres\"}";
                            } else {
                                String sql = "INSERT INTO usuarios(nome, senha) VALUES(?,?)";
                                try (Connection conexao = DriverManager.getConnection(urlBancoDados);
                                     PreparedStatement comandoSql = conexao.prepareStatement(sql)) {
                                    comandoSql.setString(1, nome);
                                    comandoSql.setString(2, senha);
                                    comandoSql.executeUpdate();
                                    respostaJson = "{\"status\": \"201\", \"mensagem\": \"Sucesso: Recurso cadastrado\"}";
                                } catch (SQLException e) {
                                    if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed: usuarios.nome")) {
                                        respostaJson = "{\"status\": \"409\", \"mensagem\": \"Erro: Recurso ja existe\"}";
                                    } else {
                                        System.err.println("Erro SQL em CRIAR_USUARIO: " + e.getMessage());
                                        respostaJson = "{\"status\": \"500\", \"mensagem\": \"Erro: Falha interna do servidor\"}";
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Erro JSON/DB em CRIAR_USUARIO: " + e.getMessage());
                            respostaJson = "{\"status\": \"422\", \"mensagem\": \"Erro: Chaves faltantes ou invalidas\"}";
                        }
                    } else if (operacao.equals("LISTAR_PROPRIO_USUARIO") ||
                            operacao.equals("LISTAR_USUARIOS") ||
                            operacao.equals("EDITAR_PROPRIO_USUARIO") ||
                            operacao.equals("EXCLUIR_PROPRIO_USUARIO") ||
                            operacao.equals("LOGOUT")) {

                        String token;
                        Claims claims = null;
                        boolean tokenInvalido = false;

                        try {
                            if (requisicao.has("token") && requisicao.get("token").isJsonPrimitive() && !requisicao.get("token").getAsString().isEmpty()) {
                                token = requisicao.get("token").getAsString();
                                claims = this.validarTokenExtrairDados(token);
                                if (claims == null) {
                                    tokenInvalido = true;
                                    respostaJson = "{\"status\": \"401\", \"mensagem\": \"Token invalido/expirado\"}";
                                }
                            } else {
                                tokenInvalido = true;
                                respostaJson = "{\"status\": \"401\", \"mensagem\": \"Token ausente/invalido\"}";
                            }
                        } catch (Exception e) {
                            tokenInvalido = true;
                            System.err.println("Erro ao extrair/validar token: " + e.getMessage());
                            respostaJson = "{\"status\": \"401\"}";
                        }

                        if (!tokenInvalido) {
                            int idUsuarioDoToken = Integer.parseInt(claims.getSubject());
                            String funcaoDoToken = claims.get("funcao", String.class);
                            boolean ehAdminDoToken = funcaoDoToken != null && funcaoDoToken.equals("admin");

                            if (operacao.equals("LISTAR_PROPRIO_USUARIO")) {
                                try {
                                    String sql = "SELECT nome FROM usuarios WHERE id = ?";
                                    try (Connection conexao = DriverManager.getConnection(urlBancoDados);
                                         PreparedStatement comandoSql = conexao.prepareStatement(sql)) {
                                        comandoSql.setInt(1, idUsuarioDoToken);
                                        try (ResultSet resultado = comandoSql.executeQuery()) {
                                            if (resultado.next()) {
                                                respostaJson = "{\"status\": \"200\", \"mensagem\": \"Sucesso: operação realizada com sucesso\", \"usuario\": \"" + resultado.getString("nome") + "\"}";
                                            } else {
                                                respostaJson = "{\"status\": \"404\", \"mensagem\": \"Erro: Recurso inexistente\"}";
                                            }
                                        }
                                    } catch (SQLException e) {
                                        System.err.println("Erro SQL em LISTAR_PROPRIO_USUARIO: " + e.getMessage());
                                        respostaJson = "{\"status\": \"500\", \"mensagem\": \"Erro: Falha interna do servidor\"}";
                                    }
                                } catch (Exception e) {
                                    System.err.println("Erro JSON/DB em LISTAR_PROPRIO_USUARIO: " + e.getMessage());
                                    respostaJson = "{\"status\": \"422\", \"mensagem\": \"Erro: Chaves faltantes ou invalidas\"}";
                                }

                            } else if (operacao.equals("EDITAR_PROPRIO_USUARIO")) {
                                try {
                                    JsonObject usuarioJson = requisicao.get("usuario").getAsJsonObject();
                                    String novaSenha = usuarioJson.get("senha").getAsString();

                                    if (novaSenha == null || novaSenha.isEmpty() || novaSenha.length() < 3) {
                                        respostaJson = "{\"status\": \"405\", \"mensagem\": \"Erro: Campos inválidos, verifique o tipo e quantidade de caracteres\"}";
                                    } else {
                                        String sql = "UPDATE usuarios SET senha = ? WHERE id = ?";
                                        try (Connection conexao = DriverManager.getConnection(urlBancoDados);
                                             PreparedStatement comandoSql = conexao.prepareStatement(sql)) {
                                            comandoSql.setString(1, novaSenha);
                                            comandoSql.setInt(2, idUsuarioDoToken);
                                            int linhasAfetadas = comandoSql.executeUpdate();

                                            if (linhasAfetadas > 0) {
                                                respostaJson = "{\"status\": \"200\", \"mensagem\": \"Sucesso: operação realizada com sucesso\"}";
                                            } else {
                                                respostaJson = "{\"status\": \"404\", \"mensagem\": \"Erro: Recurso inexistente\"}";
                                            }
                                        } catch (SQLException e) {
                                            System.err.println("Erro SQL em EDITAR_PROPRIO_USUARIO: " + e.getMessage());
                                            respostaJson = "{\"status\": \"500\", \"mensagem\": \"Erro: Falha interna do servidor\"}";
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Erro JSON em EDITAR_PROPRIO_USUARIO: " + e.getMessage());
                                    respostaJson = "{\"status\": \"422\", \"mensagem\": \"Erro: Chaves faltantes ou invalidas\"}";
                                }

                            } else if (operacao.equals("EXCLUIR_PROPRIO_USUARIO")) {
                                Connection conexao = null;
                                boolean sucesso = false;
                                try {
                                    conexao = DriverManager.getConnection(urlBancoDados);
                                    conexao.setAutoCommit(false);

                                    try (PreparedStatement psR = conexao.prepareStatement("DELETE FROM reviews WHERE id_usuario = ?")) {
                                        psR.setInt(1, idUsuarioDoToken);
                                        psR.executeUpdate();
                                    }

                                    try (PreparedStatement psU = conexao.prepareStatement("DELETE FROM usuarios WHERE id = ?")) {
                                        psU.setInt(1, idUsuarioDoToken);
                                        if (psU.executeUpdate() > 0) sucesso = true;
                                    }

                                    conexao.commit();

                                    if (sucesso) {
                                        respostaJson = "{\"status\": \"200\", \"mensagem\": \"Sucesso: operação realizada com sucesso\"}";
                                    } else {
                                        respostaJson = "{\"status\": \"404\", \"mensagem\": \"Erro: Recurso inexistente\"}";
                                    }

                                } catch (SQLException e) {
                                    if (conexao != null) try { conexao.rollback(); } catch (SQLException ex) { System.err.println("Erro rollback: " + ex.getMessage());}
                                    System.err.println("Erro SQL em EXCLUIR_PROPRIO_USUARIO: " + e.getMessage());
                                    respostaJson = "{\"status\": \"500\", \"mensagem\": \"Erro: Falha interna do servidor\"}";

                                } catch (Exception e) {
                                    System.err.println("Erro JSON/DB em EXCLUIR_PROPRIO_USUARIO: " + e.getMessage());
                                    respostaJson = "{\"status\": \"422\", \"mensagem\": \"Erro: Chaves faltantes ou invalidas\"}";

                                } finally {
                                    if (conexao != null) try { conexao.setAutoCommit(true); conexao.close(); } catch (SQLException ex) { System.err.println("Erro fechar conexao: " + ex.getMessage());}
                                }

                            } else if (operacao.equals("LOGOUT")) {
                                respostaJson = "{\"status\": \"200\", \"mensagem\": \"Sucesso: operação realizada com sucesso\"}";
                            }
                        }
                    } else {
                        respostaJson = "{\"status\": \"400\", \"mensagem\": \"Erro: Operação não encontrada ou inválida\"}";
                    }

                    if (!respostaJson.isEmpty()) {
                        System.out.println("Servidor respondeu: " + respostaJson);
                        saida.println(respostaJson);
                    } else {
                        System.err.println("AVISO: Nenhuma resposta JSON definida para op " + operacao);
                        respostaJson = "{\"status\": \"400\"}";
                        System.out.println("Servidor respondeu: " + respostaJson);
                        saida.println(respostaJson);
                    }

                } catch (Exception e) {
                    String opInfo = (operacao != null) ? operacao : "desconhecida";
                    System.err.println("Erro inesperado ao processar op '" + opInfo + "': " + e.getMessage());
                    e.printStackTrace(); // Bom para debug
                    respostaJson = "{\"status\": \"500\", \"mensagem\": \"Erro interno\"}";
                    if (!saida.checkError()) {
                        System.out.println("Servidor respondeu: " + respostaJson);
                        saida.println(respostaJson);
                    }
                }
            }

            System.out.println("Cliente desconectou.");

        } catch (IOException e) {
            System.err.println("Problema de comunicacao: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erro critico na thread: " + e.getMessage());
        } finally {
            try {
                if (this.socketCliente != null && !this.socketCliente.isClosed()) {
                    this.socketCliente.close();
                    System.out.println("Socket do cliente fechado.");
                }
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket cliente: " + e.getMessage());
            }
            System.out.println("Thread de comunicacao encerrada.");
        }
    }
}