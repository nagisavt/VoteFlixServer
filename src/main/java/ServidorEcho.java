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
    private static final String urlBancoDados = "jdbc:sqlite:votefix.db";

    private static final String CHAVE_SECRETA_STRING = "QK55qT2jmZAkPHABB1acTQmtLyObqb6E";
    private static final SecretKey CHAVE_SECRETA = Keys.hmacShaKeyFor(CHAVE_SECRETA_STRING.getBytes());
    private static final long EXPIRACAO_TOKEN_MINUTOS = 60L;

    private final Gson gson = new Gson();

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

    private class ExcecaoValidacao extends Exception {
        private final String status;
        public ExcecaoValidacao(String status, String mensagem) {
            super(mensagem);
            this.status = status;
        }
        public String getStatus() { return status; }
    }

    private class ExcecaoAutenticacao extends Exception {
        private final String status;
        public ExcecaoAutenticacao(String status, String mensagem) {
            super(mensagem);
            this.status = status;
        }
        public String getStatus() { return status; }
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

    private String criarRespostaErro(String status, String mensagem) {
        JsonObject resposta = new JsonObject();
        resposta.addProperty("status", status);
        resposta.addProperty("mensagem", mensagem);
        return gson.toJson(resposta);
    }

    private String criarRespostaSucesso(String status, String mensagem) {
        JsonObject resposta = new JsonObject();
        resposta.addProperty("status", status);
        resposta.addProperty("mensagem", mensagem);
        return gson.toJson(resposta);
    }

    private Claims autenticar(JsonObject requisicao) throws ExcecaoAutenticacao {
        String token;
        try {
            if (requisicao.has("token") && requisicao.get("token").isJsonPrimitive() && !requisicao.get("token").getAsString().isEmpty()) {
                token = requisicao.get("token").getAsString();
            } else {
                throw new ExcecaoAutenticacao("401", "Token ausente/invalido");
            }
        } catch (Exception e) {
            throw new ExcecaoAutenticacao("401", "Token ausente/invalido");
        }

        Claims claims = this.validarTokenExtrairDados(token);
        if (claims == null) {
            throw new ExcecaoAutenticacao("401", "Token invalido/expirado");
        }
        return claims;
    }

    private void autorizarAdmin(Claims claims) throws ExcecaoAutenticacao {
        String funcaoDoToken = claims.get("funcao", String.class);
        boolean ehAdminDoToken = funcaoDoToken != null && funcaoDoToken.equals("admin");

        if (!ehAdminDoToken) {
            throw new ExcecaoAutenticacao("403", "Erro: sem permissão");
        }
    }

    @Override
    public void run() {
        System.out.println("Nova thread de comunicacao iniciada.");

        try (PrintWriter saida = new PrintWriter(this.socketCliente.getOutputStream(), true);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(this.socketCliente.getInputStream()))) {

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
                        respostaJson = criarRespostaErro("400", "Erro: Operação não encontrada ou inválida (Json do cliente invalido)");
                        saida.println(respostaJson);
                        continue;
                    }

                    if (requisicao == null || !requisicao.has("operacao") || !requisicao.get("operacao").isJsonPrimitive()) {
                        respostaJson = criarRespostaErro("400", "Erro: Operação não encontrada ou inválida");
                        saida.println(respostaJson);
                        continue;
                    }

                    operacao = requisicao.get("operacao").getAsString();

                    if (operacao.equals("LOGIN")) {
                        respostaJson = tratarLogin(requisicao);

                    } else if (operacao.equals("CRIAR_USUARIO")) {
                        respostaJson = tratarCriarUsuario(requisicao);

                    } else if (operacao.equals("LISTAR_PROPRIO_USUARIO") ||
                            operacao.equals("LISTAR_USUARIOS") ||
                            operacao.equals("EDITAR_PROPRIO_USUARIO") ||
                            operacao.equals("EXCLUIR_PROPRIO_USUARIO") ||
                            operacao.equals("LOGOUT") ||
                            operacao.equals("CRIAR_FILME")  ||
                            operacao.equals("LISTAR_FILMES")  ||
                            operacao.equals("EXCLUIR_FILME")  ||
                            operacao.equals("BUSCAR_FILME_ID")  ||
                            operacao.equals("EDITAR_FILME")) {

                        Claims claims = autenticar(requisicao);
                        int idUsuarioDoToken = Integer.parseInt(claims.getSubject());

                        switch (operacao) {
                            case "LISTAR_PROPRIO_USUARIO":
                                respostaJson = tratarListarProprioUsuario(idUsuarioDoToken);
                                break;
                            case "EDITAR_PROPRIO_USUARIO":
                                respostaJson = tratarEditarProprioUsuario(requisicao, idUsuarioDoToken);
                                break;
                            case "EXCLUIR_PROPRIO_USUARIO":
                                respostaJson = tratarExcluirProprioUsuario(idUsuarioDoToken);
                                break;
                            case "LOGOUT":
                                respostaJson = tratarLogout();
                                break;
                            case "LISTAR_FILMES":
                                respostaJson = tratarListarFilmes();
                                break;
                            case "BUSCAR_FILME_ID":
                                respostaJson = tratarBuscarFilmePorId(requisicao);
                                break;
                            case "CRIAR_FILME":
                                autorizarAdmin(claims);
                                respostaJson = tratarCriarFilme(requisicao);
                                break;
                            case "EXCLUIR_FILME":
                                autorizarAdmin(claims);
                                respostaJson = tratarExcluirFilme(requisicao);
                                break;
                            case "EDITAR_FILME":
                                autorizarAdmin(claims);
                                respostaJson = tratarEditarFilme(requisicao);
                                break;
                            case "LISTAR_USUARIOS":
                                autorizarAdmin(claims);
                                respostaJson = tratarListarUsuarios();
                                break;
                        }
                    } else {
                        respostaJson = criarRespostaErro("400", "Erro: Operação não encontrada ou inválida");
                    }

                } catch (ExcecaoAutenticacao e) {
                    respostaJson = criarRespostaErro(e.getStatus(), e.getMessage());
                } catch (ExcecaoValidacao e) {
                    respostaJson = criarRespostaErro(e.getStatus(), e.getMessage());
                } catch (SQLException e) {
                    System.err.println("Erro SQL na op " + operacao + ": " + e.getMessage());
                    respostaJson = criarRespostaErro("500", "Erro: Falha interna do servidor");
                } catch (Exception e) {
                    String opInfo = (operacao != null) ? operacao : "desconhecida";
                    System.err.println("Erro inesperado ao processar op '" + opInfo + "': " + e.getMessage());
                    e.printStackTrace();
                    respostaJson = criarRespostaErro("500", "Erro interno");
                }

                if (respostaJson.isEmpty()) {
                    System.err.println("AVISO: Nenhuma resposta JSON definida para op " + operacao);
                    respostaJson = criarRespostaErro("500", "Erro: Resposta não gerada");
                }
                System.out.println("Servidor respondeu: " + respostaJson);
                saida.println(respostaJson);
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

    private String tratarLogin(JsonObject requisicao) throws ExcecaoValidacao, SQLException {
        String usuario;
        String senha;
        try {
            usuario = requisicao.get("usuario").getAsString();
            senha = requisicao.get("senha").getAsString();
        } catch (Exception e) {
            System.err.println("Erro ao obter 'usuario' ou 'senha' do JSON: " + e.getMessage());
            throw new ExcecaoValidacao("422", "Erro: Chaves faltantes ou invalidas");
        }

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

                    JsonObject resposta = new JsonObject();
                    resposta.addProperty("status", "200");
                    resposta.addProperty("mensagem", "Sucesso: operação realizada com sucesso");
                    resposta.addProperty("token", tokenJwt);
                    return gson.toJson(resposta);
                } else {
                    return criarRespostaErro("422", "Erro: Chaves faltantes ou invalidas");
                }
            }
        }
    }

    private String tratarCriarUsuario(JsonObject requisicao) throws ExcecaoValidacao, SQLException {
        String nome;
        String senha;
        try {
            JsonObject usuarioJson = requisicao.get("usuario").getAsJsonObject();
            nome = usuarioJson.get("nome").getAsString();
            senha = usuarioJson.get("senha").getAsString();
        } catch (Exception e) {
            System.err.println("Erro JSON em CRIAR_USUARIO: " + e.getMessage());
            throw new ExcecaoValidacao("422", "Erro: Chaves faltantes ou invalidas");
        }

        if (nome.isEmpty() || senha.isEmpty() || nome.length() > 50 || senha.length() < 3) {
            throw new ExcecaoValidacao("405", "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");
        }

        String sql = "INSERT INTO usuarios(nome, senha) VALUES(?,?)";
        try (Connection conexao = DriverManager.getConnection(urlBancoDados);
             PreparedStatement comandoSql = conexao.prepareStatement(sql)) {
            comandoSql.setString(1, nome);
            comandoSql.setString(2, senha);
            comandoSql.executeUpdate();
            return criarRespostaSucesso("201", "Sucesso: Recurso cadastrado");
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed: usuarios.nome")) {
                return criarRespostaErro("409", "Erro: Recurso ja existe");
            } else {
                throw e;
            }
        }
    }

    private String tratarListarProprioUsuario(int idUsuarioDoToken) throws SQLException {
        String sql = "SELECT nome FROM usuarios WHERE id = ?";
        try (Connection conexao = DriverManager.getConnection(urlBancoDados);
             PreparedStatement comandoSql = conexao.prepareStatement(sql)) {
            comandoSql.setInt(1, idUsuarioDoToken);
            try (ResultSet resultado = comandoSql.executeQuery()) {
                if (resultado.next()) {
                    JsonObject resposta = new JsonObject();
                    resposta.addProperty("status", "200");
                    resposta.addProperty("mensagem", "Sucesso: operação realizada com sucesso");
                    resposta.addProperty("usuario", resultado.getString("nome"));
                    return gson.toJson(resposta);
                } else {
                    return criarRespostaErro("404", "Erro: Recurso inexistente");
                }
            }
        }
    }

    private String tratarEditarProprioUsuario(JsonObject requisicao, int idUsuarioDoToken) throws ExcecaoValidacao, SQLException {
        String novaSenha;
        try {
            JsonObject usuarioJson = requisicao.get("usuario").getAsJsonObject();
            novaSenha = usuarioJson.get("senha").getAsString();
        } catch (Exception e) {
            System.err.println("Erro JSON em EDITAR_PROPRIO_USUARIO: " + e.getMessage());
            throw new ExcecaoValidacao("422", "Erro: Chaves faltantes ou invalidas");
        }

        if (novaSenha == null || novaSenha.isEmpty() || novaSenha.length() < 3) {
            throw new ExcecaoValidacao("405", "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");
        }

        String sql = "UPDATE usuarios SET senha = ? WHERE id = ?";
        try (Connection conexao = DriverManager.getConnection(urlBancoDados);
             PreparedStatement comandoSql = conexao.prepareStatement(sql)) {
            comandoSql.setString(1, novaSenha);
            comandoSql.setInt(2, idUsuarioDoToken);
            int linhasAfetadas = comandoSql.executeUpdate();

            if (linhasAfetadas > 0) {
                return criarRespostaSucesso("200", "Sucesso: operação realizada com sucesso");
            } else {
                return criarRespostaErro("404", "Erro: Recurso inexistente");
            }
        }
    }

    private String tratarExcluirProprioUsuario(int idUsuarioDoToken) throws SQLException {
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
                return criarRespostaSucesso("200", "Sucesso: operação realizada com sucesso");
            } else {
                return criarRespostaErro("404", "Erro: Recurso inexistente");
            }

        } catch (SQLException e) {
            if (conexao != null) try {
                conexao.rollback();
            } catch (SQLException ex) {
                System.err.println("Erro rollback: " + ex.getMessage());
            }
            throw e;
        } finally {
            if (conexao != null) try {
                conexao.setAutoCommit(true);
                conexao.close();
            } catch (SQLException ex) {
                System.err.println("Erro fechar conexao: " + ex.getMessage());
            }
        }
    }

    private String tratarLogout() {
        return criarRespostaSucesso("200", "Sucesso: operação realizada com sucesso");
    }

    private String tratarCriarFilme(JsonObject requisicao) throws ExcecaoValidacao, SQLException {
        String titulo, diretor, ano, sinopse, generos;
        JsonArray generoArray;

        try {
            JsonObject filmeJson = requisicao.get("filme").getAsJsonObject();
            titulo = filmeJson.get("titulo").getAsString();
            diretor = filmeJson.get("diretor").getAsString();
            ano = filmeJson.get("ano").getAsString();
            generoArray = filmeJson.get("genero").getAsJsonArray();
            sinopse = filmeJson.get("sinopse").getAsString();

            StringBuilder generoBuilder = new StringBuilder();
            for (int i = 0; i < generoArray.size(); i++) {
                generoBuilder.append(generoArray.get(i).getAsString());
                if (i < generoArray.size() - 1) generoBuilder.append(", ");
            }
            generos = generoBuilder.toString();

        } catch (Exception e) {
            System.err.println("Erro JSON/DB em CRIAR_FILME: " + e.getMessage());
            throw new ExcecaoValidacao("422", "Erro: Chaves faltantes ou invalidas");
        }

        if (titulo.length() < 3 || titulo.length() > 30 ||
                diretor.length() < 3 || diretor.length() > 30 ||
                ano.length() < 3 || ano.length() > 4 || !ano.matches("\\d+") ||
                sinopse.length() > 250 ||
                generoArray.isEmpty()) {
            throw new ExcecaoValidacao("405", "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");
        }

        String sqlCheck = "SELECT id FROM filmes WHERE titulo = ? AND diretor = ? AND ano = ?";
        String sqlInsert = "INSERT INTO filmes(titulo, diretor, ano, genero, sinopse) VALUES(?,?,?,?,?)";

        try (Connection conexao = DriverManager.getConnection(urlBancoDados)) {
            try (PreparedStatement comandoCheck = conexao.prepareStatement(sqlCheck)) {
                comandoCheck.setString(1, titulo);
                comandoCheck.setString(2, diretor);
                comandoCheck.setString(3, ano);
                try (ResultSet rs = comandoCheck.executeQuery()) {
                    if (rs.next()) {
                        return criarRespostaErro("409", "Erro: Recurso ja existe (filme com mesmo titulo, diretor e ano)");
                    } else {
                        try (PreparedStatement comandoInsert = conexao.prepareStatement(sqlInsert)) {
                            comandoInsert.setString(1, titulo);
                            comandoInsert.setString(2, diretor);
                            comandoInsert.setString(3, ano);
                            comandoInsert.setString(4, generos);
                            comandoInsert.setString(5, sinopse);
                            comandoInsert.executeUpdate();
                            return criarRespostaSucesso("201", "Sucesso: Recurso cadastrado");
                        }
                    }
                }
            }
        }
    }

    private String tratarListarFilmes() throws SQLException {
        JsonArray filmesArray = new JsonArray();
        String sql = "SELECT f.id, f.titulo, f.diretor, f.ano, f.genero, f.sinopse, " +
                "       COALESCE(AVG(CAST(r.nota AS REAL)), 0.0) AS nota_media, " +
                "       COUNT(r.id) AS qtd_avaliacoes " +
                "FROM filmes f " +
                "LEFT JOIN reviews r ON f.id = r.id_filme " +
                "GROUP BY f.id, f.titulo, f.diretor, f.ano, f.genero, f.sinopse";

        try (Connection conexao = DriverManager.getConnection(urlBancoDados);
             PreparedStatement comandoSql = conexao.prepareStatement(sql);
             ResultSet resultado = comandoSql.executeQuery()) {

            while (resultado.next()) {
                JsonObject filmeObj = new JsonObject();
                filmeObj.addProperty("id", String.valueOf(resultado.getInt("id")));
                filmeObj.addProperty("titulo", resultado.getString("titulo"));
                filmeObj.addProperty("diretor", resultado.getString("diretor"));
                filmeObj.addProperty("ano", resultado.getString("ano"));
                filmeObj.addProperty("sinopse", resultado.getString("sinopse"));

                String generosDB = resultado.getString("genero");
                JsonArray generosJson = new JsonArray();
                if (generosDB != null && !generosDB.isEmpty()) {
                    String[] generosArray = generosDB.split(",");
                    for (String g : generosArray) {
                        generosJson.add(g.trim());
                    }
                }
                filmeObj.add("genero", generosJson);

                filmeObj.addProperty("nota", String.format("%.1f", resultado.getDouble("nota_media")));
                filmeObj.addProperty("qtd_avaliacoes", String.valueOf(resultado.getInt("qtd_avaliacoes")));
                filmesArray.add(filmeObj);
            }

            JsonObject respostaObj = new JsonObject();
            respostaObj.addProperty("status", "200");
            respostaObj.addProperty("mensagem", "Sucesso: Operação realizada com sucesso");
            respostaObj.add("filmes", filmesArray);
            return gson.toJson(respostaObj);
        }
    }

    private String tratarExcluirFilme(JsonObject requisicao) throws ExcecaoValidacao, SQLException {
        String idFilmeParaExcluir;
        int idFilme;
        Connection conexao = null;
        boolean sucesso = false;

        try {
            idFilmeParaExcluir = requisicao.get("id").getAsString();
            if (idFilmeParaExcluir == null || idFilmeParaExcluir.isEmpty()) {
                throw new ExcecaoValidacao("422", "ID do filme ausente.");
            }
            idFilme = Integer.parseInt(idFilmeParaExcluir);
        } catch (NumberFormatException e) {
            throw new ExcecaoValidacao("422", "Erro: ID do filme inválido");
        } catch (Exception e) {
            throw new ExcecaoValidacao("422", "Erro: Chaves faltantes ou invalidas");
        }

        try {
            conexao = DriverManager.getConnection(urlBancoDados);
            conexao.setAutoCommit(false);

            String sqlDeleteReviews = "DELETE FROM reviews WHERE id_filme = ?";
            try (PreparedStatement psReviews = conexao.prepareStatement(sqlDeleteReviews)) {
                psReviews.setInt(1, idFilme);
                psReviews.executeUpdate();
            }

            String sqlDeleteFilme = "DELETE FROM filmes WHERE id = ?";
            try (PreparedStatement psFilme = conexao.prepareStatement(sqlDeleteFilme)) {
                psFilme.setInt(1, idFilme);
                int linhasAfetadas = psFilme.executeUpdate();
                if (linhasAfetadas > 0) {
                    sucesso = true;
                }
            }

            if (sucesso) {
                conexao.commit();
                return criarRespostaSucesso("200", "Sucesso: operação realizada com sucesso");
            } else {
                conexao.rollback();
                return criarRespostaErro("404", "Erro: Recurso inexistente");
            }
        } catch (SQLException e) {
            if (conexao != null) try { conexao.rollback(); } catch (SQLException ex) {}
            throw e;
        } finally {
            if (conexao != null) try { conexao.setAutoCommit(true); conexao.close(); } catch (SQLException ex) {}
        }
    }

    private String tratarListarUsuarios() throws SQLException {
        JsonArray usuariosArray = new JsonArray();
        String sql = "SELECT id, nome FROM usuarios";

        try (Connection conexao = DriverManager.getConnection(urlBancoDados);
             PreparedStatement comandoSql = conexao.prepareStatement(sql);
             ResultSet resultado = comandoSql.executeQuery()) {

            while (resultado.next()) {
                JsonObject usuarioObj = new JsonObject();
                usuarioObj.addProperty("id", String.valueOf(resultado.getInt("id")));
                usuarioObj.addProperty("nome", resultado.getString("nome"));
                usuariosArray.add(usuarioObj);
            }

            JsonObject respostaObj = new JsonObject();
            respostaObj.addProperty("status", "200");
            respostaObj.addProperty("mensagem", "Sucesso: operação realizada com sucesso");
            respostaObj.add("usuarios", usuariosArray);
            return gson.toJson(respostaObj);
        }
    }

    private String tratarBuscarFilmePorId(JsonObject requisicao) throws ExcecaoValidacao, SQLException {
        JsonObject filmeObj = null;
        JsonArray reviewsArray = new JsonArray();
        int idFilme;

        try {
            String idFilmeReq = requisicao.get("id_filme").getAsString();
            idFilme = Integer.parseInt(idFilmeReq);
        } catch (Exception e) {
            throw new ExcecaoValidacao("422", "Erro: Chaves faltantes ou invalidas (id_filme)");
        }

        try (Connection conexao = DriverManager.getConnection(urlBancoDados)) {
            String sqlFilme = "SELECT f.id, f.titulo, f.diretor, f.ano, f.genero, f.sinopse, " +
                    "       COALESCE(AVG(CAST(r.nota AS REAL)), 0.0) AS nota_media, " +
                    "       COUNT(r.id) AS qtd_avaliacoes " +
                    "FROM filmes f " +
                    "LEFT JOIN reviews r ON f.id = r.id_filme " +
                    "WHERE f.id = ? " +
                    "GROUP BY f.id, f.titulo, f.diretor, f.ano, f.genero, f.sinopse";

            try (PreparedStatement comandoFilme = conexao.prepareStatement(sqlFilme)) {
                comandoFilme.setInt(1, idFilme);
                try (ResultSet rsFilme = comandoFilme.executeQuery()) {
                    if (rsFilme.next()) {
                        filmeObj = new JsonObject();
                        filmeObj.addProperty("id", String.valueOf(rsFilme.getInt("id")));
                        filmeObj.addProperty("titulo", rsFilme.getString("titulo"));
                        filmeObj.addProperty("diretor", rsFilme.getString("diretor"));
                        filmeObj.addProperty("ano", rsFilme.getString("ano"));
                        filmeObj.addProperty("sinopse", rsFilme.getString("sinopse"));
                        JsonArray generosJson = new JsonArray();
                        String generosDB = rsFilme.getString("genero");
                        if (generosDB != null && !generosDB.isEmpty()) {
                            for (String g : generosDB.split(",")) {
                                generosJson.add(g.trim());
                            }
                        }
                        filmeObj.add("genero", generosJson);
                        filmeObj.addProperty("nota", String.format("%.1f", rsFilme.getDouble("nota_media")));
                        filmeObj.addProperty("qtd_avaliacoes", String.valueOf(rsFilme.getInt("qtd_avaliacoes")));
                    }
                }
            }

            if (filmeObj == null) {
                return criarRespostaErro("404", "Erro: Recurso inexistente");
            } else {
                String sqlReviews = "SELECT r.id, r.id_filme, u.nome AS nome_usuario, " +
                        "       r.nota, r.titulo, r.descricao, r.data " +
                        "FROM reviews r JOIN usuarios u ON r.id_usuario = u.id " +
                        "WHERE r.id_filme = ?";

                try (PreparedStatement comandoReviews = conexao.prepareStatement(sqlReviews)) {
                    comandoReviews.setInt(1, idFilme);
                    try (ResultSet rsReviews = comandoReviews.executeQuery()) {
                        while (rsReviews.next()) {
                            JsonObject reviewObj = new JsonObject();
                            reviewObj.addProperty("id", String.valueOf(rsReviews.getInt("id")));
                            reviewObj.addProperty("id_filme", String.valueOf(rsReviews.getInt("id_filme")));
                            reviewObj.addProperty("nome_usuario", rsReviews.getString("nome_usuario"));
                            reviewObj.addProperty("nota", rsReviews.getString("nota"));
                            reviewObj.addProperty("titulo", rsReviews.getString("titulo"));
                            reviewObj.addProperty("descricao", rsReviews.getString("descricao"));
                            reviewObj.addProperty("data", rsReviews.getString("data"));
                            reviewsArray.add(reviewObj);
                        }
                    }
                }

                JsonObject respostaObj = new JsonObject();
                respostaObj.addProperty("status", "200");
                respostaObj.addProperty("mensagem", "Sucesso: operação realizada com sucesso");
                respostaObj.add("filme", filmeObj);
                respostaObj.add("reviews", reviewsArray);
                return gson.toJson(respostaObj);
            }
        }
    }

    private String tratarEditarFilme(JsonObject requisicao) throws ExcecaoValidacao, SQLException {
        String titulo, diretor, ano, sinopse, generos, idFilmeStr;
        JsonArray generoArray;
        int idFilme;

        try {
            JsonObject filmeJson = requisicao.get("filme").getAsJsonObject();
            idFilmeStr = filmeJson.get("id").getAsString();
            titulo = filmeJson.get("titulo").getAsString();
            diretor = filmeJson.get("diretor").getAsString();
            ano = filmeJson.get("ano").getAsString();
            generoArray = filmeJson.get("genero").getAsJsonArray();
            sinopse = filmeJson.get("sinopse").getAsString();

            idFilme = Integer.parseInt(idFilmeStr);

            StringBuilder generoBuilder = new StringBuilder();
            for (int i = 0; i < generoArray.size(); i++) {
                generoBuilder.append(generoArray.get(i).getAsString());
                if (i < generoArray.size() - 1) generoBuilder.append(", ");
            }
            generos = generoBuilder.toString();

        } catch (NumberFormatException e) {
            throw new ExcecaoValidacao("422", "Erro: ID do filme inválido");
        } catch (Exception e) {
            System.err.println("Erro JSON/DB em EDITAR_FILME: " + e.getMessage());
            throw new ExcecaoValidacao("422", "Erro: Chaves faltantes ou invalidas");
        }

        if (titulo.length() < 3 || titulo.length() > 30 ||
                diretor.length() < 3 || diretor.length() > 30 ||
                ano.length() < 3 || ano.length() > 4 || !ano.matches("\\d+") ||
                sinopse.length() > 250 ||
                generoArray.isEmpty()) {
            throw new ExcecaoValidacao("405", "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");
        }

        String sqlCheck = "SELECT id FROM filmes WHERE titulo = ? AND diretor = ? AND ano = ? AND id != ?";
        String sqlUpdate = "UPDATE filmes SET titulo = ?, diretor = ?, ano = ?, genero = ?, sinopse = ? WHERE id = ?";

        try (Connection conexao = DriverManager.getConnection(urlBancoDados)) {
            try (PreparedStatement comandoCheck = conexao.prepareStatement(sqlCheck)) {
                comandoCheck.setString(1, titulo);
                comandoCheck.setString(2, diretor);
                comandoCheck.setString(3, ano);
                comandoCheck.setInt(4, idFilme);
                try (ResultSet rs = comandoCheck.executeQuery()) {
                    if (rs.next()) {
                        return criarRespostaErro("409", "Erro: Recurso ja existe (outro filme com mesmo titulo, diretor e ano)");
                    } else {
                        try (PreparedStatement comandoUpdate = conexao.prepareStatement(sqlUpdate)) {
                            comandoUpdate.setString(1, titulo);
                            comandoUpdate.setString(2, diretor);
                            comandoUpdate.setString(3, ano);
                            comandoUpdate.setString(4, generos);
                            comandoUpdate.setString(5, sinopse);
                            comandoUpdate.setInt(6, idFilme);

                            int linhasAfetadas = comandoUpdate.executeUpdate();

                            if (linhasAfetadas > 0) {
                                return criarRespostaSucesso("200", "Sucesso: operação realizada com sucesso");
                            } else {
                                return criarRespostaErro("404", "Erro: Recurso inexistente (ID do filme não encontrado)");
                            }
                        }
                    }
                }
            }
        }
    }
}