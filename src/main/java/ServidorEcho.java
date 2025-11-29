import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ServidorEcho extends Thread {

    protected Socket socketCliente;
    private ServidorGUI gui;
    private String usuarioLogadoAtual = null;

    private static final String urlBancoDados = "jdbc:sqlite:votefix.db";
    private static final String CHAVE_SECRETA_STRING = "QK55qT2jmZAkPHABB1acTQmtLyObqb6E";
    private static final SecretKey CHAVE_SECRETA = Keys.hmacShaKeyFor(CHAVE_SECRETA_STRING.getBytes());

    public ServidorEcho(Socket clientSocket, ServidorGUI gui) {
        this.socketCliente = clientSocket;
        this.gui = gui;
        inicializarBanco();
    }

    private void log(String msg) {
        System.out.println(msg);
        if (gui != null) gui.log(msg);
    }

    private void inicializarBanco() {
        String sqlUsuarios = "CREATE TABLE IF NOT EXISTS usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT UNIQUE, senha TEXT, is_admin INTEGER DEFAULT 0)";

        String sqlFilmes = "CREATE TABLE IF NOT EXISTS filmes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "titulo TEXT, " +
                "diretor TEXT, " +
                "ano TEXT, " +
                "genero TEXT, " +
                "sinopse TEXT, " +
                "nota REAL DEFAULT 0.0, " +
                "votos INTEGER DEFAULT 0)";

        String sqlReviews = "CREATE TABLE IF NOT EXISTS reviews (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "id_filme INTEGER, " +
                "nome_usuario TEXT, " +
                "titulo TEXT, " +
                "descricao TEXT, " +
                "nota INTEGER, " +
                "data TEXT, " +
                "editado INTEGER DEFAULT 0, " +
                "FOREIGN KEY(id_filme) REFERENCES filmes(id))";

        try (Connection conn = DriverManager.getConnection(urlBancoDados);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlUsuarios);
            stmt.execute(sqlFilmes);
            stmt.execute(sqlReviews);

            try {
                stmt.execute("INSERT INTO usuarios(nome, senha, is_admin) VALUES('admin', 'admin', 1)");
            } catch (SQLException ignored) {}

        } catch (SQLException e) {
            System.out.println("Erro DB Init: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try (
                PrintWriter out = new PrintWriter(socketCliente.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socketCliente.getInputStream()));
        ) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {

                log("RECEBIDO: " + inputLine);

                try {
                    Gson gson = new Gson();
                    JsonObject jsonRequest = gson.fromJson(inputLine, JsonObject.class);

                    if (!jsonRequest.has("operacao")) {
                        out.println(gson.toJson(criarRespostaErro("400", "Operação não especificada.")));
                        continue;
                    }

                    String operacao = jsonRequest.get("operacao").getAsString();
                    JsonObject resposta;

                    if (operacao.equals("LOGIN")) resposta = login(jsonRequest);
                    else if (operacao.equals("CRIAR_USUARIO")) resposta = criarUsuario(jsonRequest);
                    else if (operacao.equals("LISTAR_FILMES")) resposta = listarFilmes(jsonRequest);
                    else if (operacao.equals("BUSCAR_FILME_ID")) resposta = buscarFilmePorId(jsonRequest);
                    else if (operacao.equals("LOGOUT")) {
                        resposta = criarRespostaSucesso("200", "Logout realizado com sucesso.");
                        if(usuarioLogadoAtual != null && gui != null) {
                            gui.removerUsuarioDaLista(usuarioLogadoAtual);
                            usuarioLogadoAtual = null;
                        }
                    }
                    else resposta = verificarAuthEExecutar(jsonRequest, operacao);

                    String jsonResponse = gson.toJson(resposta);

                    log("ENVIADO: " + jsonResponse);
                    log("");

                    out.println(jsonResponse);

                } catch (Exception e) {
                    e.printStackTrace();
                    JsonObject erro = criarRespostaErro("500", "Erro Interno: " + e.getMessage());
                    String jsonErro = new Gson().toJson(erro);
                    log("ENVIADO (ERRO): " + jsonErro);
                    out.println(jsonErro);
                }
            }
        } catch (IOException e) {
        } finally {
            if(usuarioLogadoAtual != null && gui != null) {
                gui.removerUsuarioDaLista(usuarioLogadoAtual);
                log("Usuário " + usuarioLogadoAtual + " saiu (Conexão encerrada).");
            }
        }
    }

    private JsonObject verificarAuthEExecutar(JsonObject req, String operacao) {
        if (!req.has("token")) return criarRespostaErro("401", "Token não fornecido.");
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(CHAVE_SECRETA).build().parseClaimsJws(req.get("token").getAsString()).getBody();
            String usuario = claims.getSubject();
            boolean isAdmin = claims.get("admin", Boolean.class);

            switch (operacao) {
                case "CRIAR_FILME": return isAdmin ? criarFilme(req) : criarRespostaErro("403", "Apenas administradores.");
                case "EDITAR_FILME": return isAdmin ? editarFilme(req) : criarRespostaErro("403", "Apenas administradores.");
                case "EXCLUIR_FILME": return isAdmin ? excluirFilme(req) : criarRespostaErro("403", "Apenas administradores.");
                case "LISTAR_USUARIOS": return isAdmin ? listarUsuarios() : criarRespostaErro("403", "Apenas administradores.");
                case "ADMIN_EDITAR_USUARIO": return isAdmin ? adminEditarUsuario(req) : criarRespostaErro("403", "Apenas administradores.");
                case "ADMIN_EXCLUIR_USUARIO": return isAdmin ? adminExcluirUsuario(req) : criarRespostaErro("403", "Apenas administradores.");
                case "VERIFICAR_REVIEW_USUARIO": return verificarReviewUsuario(req, usuario);
                case "CRIAR_REVIEW": return criarReview(req, usuario, isAdmin);
                case "EDITAR_REVIEW": return editarReview(req, usuario);
                case "EXCLUIR_REVIEW": return excluirReview(req, usuario, isAdmin);
                case "LISTAR_REVIEWS_USUARIO": return listarReviewsProprioUsuario(usuario);
                case "LISTAR_PROPRIO_USUARIO": return listarProprioUsuario(usuario);
                case "EXCLUIR_PROPRIO_USUARIO": return excluirProprioUsuario(usuario);
                case "EDITAR_PROPRIO_USUARIO": return editarProprioUsuario(req, usuario);

                default: return criarRespostaErro("400", "Operação desconhecida.");
            }
        } catch (Exception e) {
            return criarRespostaErro("401", "Token inválido ou expirado.");
        }
    }

    private JsonObject login(JsonObject req) {
        String u = req.get("usuario").getAsString();
        String s = req.get("senha").getAsString();
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM usuarios WHERE nome=? AND senha=?");
            ps.setString(1, u); ps.setString(2, s);
            ResultSet rs = ps.executeQuery();
            if(rs.next()) {
                String token = gerarToken(u, rs.getInt("is_admin") == 1);
                usuarioLogadoAtual = u;
                if(gui != null) gui.adicionarUsuarioNaLista(u);

                JsonObject r = criarRespostaSucesso("200", "Login realizado com sucesso.");
                r.addProperty("token", token);
                return r;
            }
            return criarRespostaErro("401", "Credenciais inválidas.");
        } catch (SQLException e) { return criarRespostaErro("500", "Erro no banco de dados."); }
    }

    private JsonObject excluirReview(JsonObject req, String usuario, boolean isAdmin) {
        if (!req.has("id")) return criarRespostaErro("422", "ID da review faltando.");
        String idReview = req.get("id").getAsString();

        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            String sqlBusca;
            PreparedStatement psBusca;

            if (isAdmin) {
                sqlBusca = "SELECT nota, id_filme FROM reviews WHERE id=?";
                psBusca = conn.prepareStatement(sqlBusca);
                psBusca.setString(1, idReview);
            } else {
                sqlBusca = "SELECT nota, id_filme FROM reviews WHERE id=? AND nome_usuario=?";
                psBusca = conn.prepareStatement(sqlBusca);
                psBusca.setString(1, idReview);
                psBusca.setString(2, usuario);
            }

            ResultSet rsRev = psBusca.executeQuery();

            if (!rsRev.next()) return criarRespostaErro("404", "Review não encontrada ou você não tem permissão.");

            int notaRemovida = rsRev.getInt("nota");
            int idFilme = rsRev.getInt("id_filme");

            String sqlDel;
            PreparedStatement psDel;

            if (isAdmin) {
                sqlDel = "DELETE FROM reviews WHERE id=?";
                psDel = conn.prepareStatement(sqlDel);
                psDel.setString(1, idReview);
            } else {
                sqlDel = "DELETE FROM reviews WHERE id=? AND nome_usuario=?";
                psDel = conn.prepareStatement(sqlDel);
                psDel.setString(1, idReview);
                psDel.setString(2, usuario);
            }
            psDel.executeUpdate();

            String sqlFilme = "SELECT nota, votos FROM filmes WHERE id=?";
            PreparedStatement psFilme = conn.prepareStatement(sqlFilme);
            psFilme.setInt(1, idFilme);
            ResultSet rsFilme = psFilme.executeQuery();

            if (rsFilme.next()) {
                double mediaAtual = rsFilme.getDouble("nota");
                int votosAtuais = rsFilme.getInt("votos");
                double novaMedia = 0.0;
                int novosVotos = votosAtuais - 1;

                if (novosVotos > 0) {
                    novaMedia = ((mediaAtual * votosAtuais) - notaRemovida) / novosVotos;
                }

                String sqlUpd = "UPDATE filmes SET nota=?, votos=? WHERE id=?";
                PreparedStatement psUpd = conn.prepareStatement(sqlUpd);
                psUpd.setDouble(1, novaMedia);
                psUpd.setInt(2, novosVotos);
                psUpd.setInt(3, idFilme);
                psUpd.executeUpdate();
            }

            return criarRespostaSucesso("200", "Review excluída com sucesso.");

        } catch (SQLException e) { return criarRespostaErro("500", "Erro interno."); }
    }

    private JsonObject adminEditarUsuario(JsonObject req) {
        if (!req.has("id") || !req.has("usuario")) return criarRespostaErro("422", "Chaves faltantes.");
        String idAlvo = req.get("id").getAsString();
        JsonObject userObj = req.getAsJsonObject("usuario");
        if (!userObj.has("senha")) return criarRespostaErro("422", "Senha faltante.");
        String novaSenha = userObj.get("senha").getAsString();
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            String sql = "UPDATE usuarios SET senha = ? WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, novaSenha);
            pstmt.setString(2, idAlvo);
            if (pstmt.executeUpdate() > 0) return criarRespostaSucesso("200", "Operação realizada com sucesso");
            else return criarRespostaErro("404", "Usuário não encontrado.");
        } catch (SQLException e) { return criarRespostaErro("500", "Erro interno."); }
    }

    private JsonObject adminExcluirUsuario(JsonObject req) {
        if (!req.has("id")) return criarRespostaErro("422", "ID faltante.");
        String idAlvo = req.get("id").getAsString();
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            String sql = "DELETE FROM usuarios WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, idAlvo);
            if (pstmt.executeUpdate() > 0) return criarRespostaSucesso("200", "Operação realizada com sucesso");
            else return criarRespostaErro("404", "Usuário não encontrado.");
        } catch (SQLException e) { return criarRespostaErro("500", "Erro interno."); }
    }

    private JsonObject listarReviewsProprioUsuario(String usuario) {
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            String sql = "SELECT * FROM reviews WHERE nome_usuario = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, usuario);
            ResultSet rs = ps.executeQuery();
            JsonArray reviews = new JsonArray();
            while (rs.next()) {
                JsonObject rev = new JsonObject();
                rev.addProperty("id", String.valueOf(rs.getInt("id")));
                rev.addProperty("id_filme", String.valueOf(rs.getInt("id_filme")));
                rev.addProperty("nome_usuario", rs.getString("nome_usuario"));
                rev.addProperty("nota", String.valueOf(rs.getInt("nota")));
                rev.addProperty("titulo", rs.getString("titulo"));
                rev.addProperty("descricao", rs.getString("descricao"));
                rev.addProperty("data", rs.getString("data"));
                rev.addProperty("editado", rs.getInt("editado") == 1 ? "true" : "false");
                reviews.add(rev);
            }
            JsonObject resp = criarRespostaSucesso("200", "Operação realizada com sucesso");
            resp.add("reviews", reviews);
            return resp;
        } catch (SQLException e) { return criarRespostaErro("500", "Erro interno."); }
    }

    private JsonObject listarUsuarios() {
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            String sql = "SELECT id, nome FROM usuarios";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            JsonArray lista = new JsonArray();
            while (rs.next()) {
                JsonObject u = new JsonObject();
                u.addProperty("id", rs.getInt("id"));
                u.addProperty("nome", rs.getString("nome"));
                lista.add(u);
            }
            JsonObject r = criarRespostaSucesso("200", "Operação realizada com sucesso.");
            r.add("usuarios", lista);
            return r;
        } catch (SQLException e) { return criarRespostaErro("500", "Erro BD."); }
    }

    private JsonObject listarProprioUsuario(String usuario) {
        JsonObject r = criarRespostaSucesso("200", "Operação realizada com sucesso.");
        r.addProperty("usuario", usuario);
        return r;
    }

    private JsonObject editarProprioUsuario(JsonObject req, String usuario) {
        if (!req.has("usuario")) return criarRespostaErro("422", "Chaves faltantes.");
        JsonObject u = req.getAsJsonObject("usuario");
        if(u.has("senha")) {
            try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
                PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET senha=? WHERE nome=?");
                ps.setString(1, u.get("senha").getAsString());
                ps.setString(2, usuario);
                ps.executeUpdate();
                return criarRespostaSucesso("200", "Operação realizada com sucesso");
            } catch (SQLException e) { return criarRespostaErro("500", "Erro BD."); }
        }
        return criarRespostaErro("400", "Nada a alterar.");
    }

    private JsonObject listarFilmes(JsonObject req) {
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            String sql = "SELECT * FROM filmes";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            JsonArray lista = new JsonArray();
            while (rs.next()) {
                JsonObject f = new JsonObject();
                f.addProperty("id", rs.getInt("id"));
                f.addProperty("titulo", rs.getString("titulo"));
                f.addProperty("diretor", rs.getString("diretor"));
                f.addProperty("ano", rs.getString("ano"));
                f.addProperty("nota", String.format("%.1f", rs.getDouble("nota")).replace(',', '.'));
                f.addProperty("qtd_avaliacoes", rs.getInt("votos"));
                lista.add(f);
            }
            JsonObject r = criarRespostaSucesso("200", "Operação realizada com sucesso.");
            r.add("filmes", lista);
            return r;
        } catch (SQLException e) { return criarRespostaErro("500", "Erro BD."); }
    }

    private JsonObject buscarFilmePorId(JsonObject req) {
        String id = req.get("id_filme").getAsString();
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM filmes WHERE id=?");
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if(!rs.next()) return criarRespostaErro("404", "Filme não encontrado.");
            JsonObject filme = new JsonObject();
            filme.addProperty("id", rs.getInt("id"));
            filme.addProperty("titulo", rs.getString("titulo"));
            filme.addProperty("sinopse", rs.getString("sinopse"));
            filme.addProperty("diretor", rs.getString("diretor"));
            filme.addProperty("ano", rs.getString("ano"));
            filme.addProperty("nota", String.format("%.1f", rs.getDouble("nota")).replace(',', '.'));
            filme.addProperty("qtd_avaliacoes", rs.getInt("votos"));
            JsonArray ja = new JsonArray();
            if(rs.getString("genero") != null) for(String s : rs.getString("genero").split(",")) ja.add(s);
            filme.add("genero", ja);

            PreparedStatement psRev = conn.prepareStatement("SELECT * FROM reviews WHERE id_filme=?");
            psRev.setString(1, id);
            ResultSet rsRev = psRev.executeQuery();
            JsonArray reviews = new JsonArray();
            while(rsRev.next()) {
                JsonObject rev = new JsonObject();
                rev.addProperty("id", rsRev.getInt("id"));
                rev.addProperty("id_filme", rsRev.getInt("id_filme"));
                rev.addProperty("nome_usuario", rsRev.getString("nome_usuario"));
                rev.addProperty("titulo", rsRev.getString("titulo"));
                rev.addProperty("descricao", rsRev.getString("descricao"));
                rev.addProperty("nota", rsRev.getString("nota"));
                rev.addProperty("data", rsRev.getString("data"));
                rev.addProperty("editado", rsRev.getInt("editado") == 1 ? "true" : "false");
                reviews.add(rev);
            }
            JsonObject resp = criarRespostaSucesso("200", "Operação realizada com sucesso.");
            resp.add("filme", filme);
            resp.add("reviews", reviews);
            return resp;
        } catch (SQLException e) { return criarRespostaErro("500", "Erro BD."); }
    }

    private JsonObject criarReview(JsonObject req, String usuario, boolean isAdmin) {
        if (isAdmin) return criarRespostaErro("403", "Administradores não podem realizar avaliações.");
        JsonObject rev = req.getAsJsonObject("review");
        String idFilme = rev.get("id_filme").getAsString();
        int notaNova;
        try {
            notaNova = Integer.parseInt(rev.get("nota").getAsString());
            if(notaNova < 1 || notaNova > 5) return criarRespostaErro("405", "A nota deve ser entre 1 e 5.");
        } catch (Exception e) { return criarRespostaErro("405", "Formato de nota inválido."); }

        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            PreparedStatement psFilme = conn.prepareStatement("SELECT nota, votos FROM filmes WHERE id=?");
            psFilme.setString(1, idFilme);
            ResultSet rsFilme = psFilme.executeQuery();
            if (!rsFilme.next()) return criarRespostaErro("404", "Filme não encontrado.");

            double mediaAtual = rsFilme.getDouble("nota");
            int votosAtuais = rsFilme.getInt("votos");

            PreparedStatement psDup = conn.prepareStatement("SELECT id FROM reviews WHERE id_filme=? AND nome_usuario=?");
            psDup.setString(1, idFilme);
            psDup.setString(2, usuario);
            if(psDup.executeQuery().next()) return criarRespostaErro("409", "Você já avaliou este filme.");

            double novaMedia = ((mediaAtual * votosAtuais) + notaNova) / (votosAtuais + 1);
            int novosVotos = votosAtuais + 1;

            String sqlUpdateFilme = "UPDATE filmes SET nota=?, votos=? WHERE id=?";
            PreparedStatement psUpd = conn.prepareStatement(sqlUpdateFilme);
            psUpd.setDouble(1, novaMedia);
            psUpd.setInt(2, novosVotos);
            psUpd.setString(3, idFilme);
            psUpd.executeUpdate();

            String dataAtual = new SimpleDateFormat("dd/MM/yyyy").format(new Date());
            String sqlInsertRev = "INSERT INTO reviews(id_filme, nome_usuario, titulo, descricao, nota, data, editado) VALUES(?,?,?,?,?,?,0)";
            PreparedStatement psIns = conn.prepareStatement(sqlInsertRev);
            psIns.setString(1, idFilme);
            psIns.setString(2, usuario);
            psIns.setString(3, rev.get("titulo").getAsString());
            psIns.setString(4, rev.get("descricao").getAsString());
            psIns.setInt(5, notaNova);
            psIns.setString(6, dataAtual);
            psIns.executeUpdate();

            return criarRespostaSucesso("201", "Avaliação criada com sucesso.");
        } catch (SQLException e) { return criarRespostaErro("500", "Erro BD."); }
    }

    private JsonObject verificarReviewUsuario(JsonObject req, String usuario) {
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM reviews WHERE id_filme=? AND nome_usuario=?");
            ps.setString(1, req.get("id_filme").getAsString());
            ps.setString(2, usuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                JsonObject r = new JsonObject();
                r.addProperty("id", rs.getInt("id"));
                r.addProperty("titulo", rs.getString("titulo"));
                r.addProperty("descricao", rs.getString("descricao"));
                r.addProperty("nota", rs.getString("nota"));
                JsonObject resp = criarRespostaSucesso("200", "Review encontrada.");
                resp.add("review", r);
                return resp;
            } else return criarRespostaErro("404", "Nenhuma review encontrada.");
        } catch (SQLException e) { return criarRespostaErro("500", "Erro BD."); }
    }

    private JsonObject editarReview(JsonObject req, String usuario) {
        JsonObject rev = req.getAsJsonObject("review");
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            String sql = "UPDATE reviews SET titulo=?, descricao=?, nota=?, editado=1 WHERE id_filme=? AND nome_usuario=?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, rev.get("titulo").getAsString());
            ps.setString(2, rev.get("descricao").getAsString());
            ps.setString(3, rev.get("nota").getAsString());
            ps.setString(4, rev.get("id_filme").getAsString());
            ps.setString(5, usuario);
            if(ps.executeUpdate() > 0) return criarRespostaSucesso("200", "Review atualizada com sucesso.");
            else return criarRespostaErro("404", "Review não encontrada.");
        } catch (SQLException e) { return criarRespostaErro("500", "Erro BD."); }
    }

    private JsonObject criarFilme(JsonObject req) {
        JsonObject f = req.getAsJsonObject("filme");
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            String sql = "INSERT INTO filmes(titulo, diretor, ano, genero, sinopse, nota, votos) VALUES(?,?,?,?,?,0.0,0)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, f.get("titulo").getAsString());
            ps.setString(2, f.get("diretor").getAsString());
            ps.setString(3, f.get("ano").getAsString());
            JsonArray ga = f.getAsJsonArray("genero");
            StringBuilder sb = new StringBuilder();
            for(int i=0;i<ga.size();i++) { sb.append(ga.get(i).getAsString()); if(i<ga.size()-1) sb.append(","); }
            ps.setString(4, sb.toString());
            ps.setString(5, f.has("sinopse")?f.get("sinopse").getAsString():"");
            ps.executeUpdate();
            return criarRespostaSucesso("201", "Filme criado com sucesso.");
        } catch (SQLException e) { return criarRespostaErro("500", "Erro BD."); }
    }

    private JsonObject editarFilme(JsonObject req) {
        JsonObject f = req.getAsJsonObject("filme");
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            String sql = "UPDATE filmes SET titulo=?, diretor=?, ano=?, genero=?, sinopse=? WHERE id=?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, f.get("titulo").getAsString());
            ps.setString(2, f.get("diretor").getAsString());
            ps.setString(3, f.get("ano").getAsString());
            JsonArray ga = f.getAsJsonArray("genero");
            StringBuilder sb = new StringBuilder();
            for(int i=0;i<ga.size();i++) { sb.append(ga.get(i).getAsString()); if(i<ga.size()-1) sb.append(","); }
            ps.setString(4, sb.toString());
            ps.setString(5, f.has("sinopse")?f.get("sinopse").getAsString():"");
            ps.setString(6, f.get("id").getAsString());
            ps.executeUpdate();
            return criarRespostaSucesso("200", "Filme atualizado com sucesso.");
        } catch (SQLException e) { return criarRespostaErro("500", "Erro BD."); }
    }

    private JsonObject excluirFilme(JsonObject req) {
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            PreparedStatement ps = conn.prepareStatement("DELETE FROM filmes WHERE id=?");
            ps.setString(1, req.get("id").getAsString());
            return ps.executeUpdate() > 0 ? criarRespostaSucesso("200", "Filme excluído com sucesso.") : criarRespostaErro("404", "Filme não encontrado.");
        } catch (SQLException e) { return criarRespostaErro("500", "Erro BD."); }
    }

    private JsonObject criarUsuario(JsonObject req) {
        JsonObject u = req.getAsJsonObject("usuario");
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            PreparedStatement check = conn.prepareStatement("SELECT nome FROM usuarios WHERE nome=?");
            check.setString(1, u.get("nome").getAsString());
            if(check.executeQuery().next()) return criarRespostaErro("409", "Usuário já existe.");
            PreparedStatement ps = conn.prepareStatement("INSERT INTO usuarios(nome, senha, is_admin) VALUES(?,?,?)");
            ps.setString(1, u.get("nome").getAsString());
            ps.setString(2, u.get("senha").getAsString());
            ps.setInt(3, 0);
            ps.executeUpdate();
            return criarRespostaSucesso("201", "Usuário criado com sucesso.");
        } catch (SQLException e) { return criarRespostaErro("500", "Erro BD."); }
    }

    private JsonObject excluirProprioUsuario(String usuario) {
        try (Connection conn = DriverManager.getConnection(urlBancoDados)) {
            PreparedStatement ps = conn.prepareStatement("DELETE FROM usuarios WHERE nome=?");
            ps.setString(1, usuario);
            ps.executeUpdate();
            return criarRespostaSucesso("200", "Conta excluída com sucesso.");
        } catch (SQLException e) { return criarRespostaErro("500", "Erro BD."); }
    }

    private String gerarToken(String user, boolean admin) {
        return Jwts.builder().setSubject(user).claim("admin", admin)
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(CHAVE_SECRETA).compact();
    }

    private JsonObject criarRespostaSucesso(String s, String m) {
        JsonObject j = new JsonObject(); j.addProperty("status", s); j.addProperty("mensagem", m); return j;
    }
    private JsonObject criarRespostaErro(String s, String m) {
        JsonObject j = new JsonObject(); j.addProperty("status", s); j.addProperty("mensagem", m); return j;
    }
}