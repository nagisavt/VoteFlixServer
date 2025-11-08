import java.net.*;
import java.io.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

public class ClienteEcho {

    private static final Gson gson = new Gson();

    private static class Sessao {
        String tokenLogado = null;
    }

    private static void imprimirMenu() {
        System.out.println("\n--- Comandos Disponiveis ---");
        System.out.println("Gerais:");
        System.out.println("  login          : Entrar no sistema");
        System.out.println("  criarusuario   : Registrar uma nova conta");
        System.out.println("\nUsuario Logado:");
        System.out.println("  listarfilmes   : Ver todos os filmes cadastrados");
        System.out.println("  buscarfilme    : Ver detalhes e reviews de um filme (pelo ID)");
        // System.out.println("  criarreview    : Adicionar uma review a um filme");
        // System.out.println("  minhasreviews  : Listar todas as suas reviews");
        // System.out.println("  editarreview   : Modificar uma de suas reviews (pelo ID da review)");
        // System.out.println("  excluirreview  : Apagar uma de suas reviews (pelo ID da review)");
        System.out.println("  verperfil      : Ver o nome do seu usuario");
        System.out.println("  editaruser     : Editar sua propria conta (mudar senha)");
        System.out.println("  excluiruser    : Apagar sua propria conta");
        System.out.println("  logout         : Sair da conta atual");
        System.out.println("\nAdministrador:");
        System.out.println("  criarfilme     : Adicionar um novo filme ao catalogo");
        System.out.println("  editarfilme    : Modificar um filme existente (pelo ID)");
        System.out.println("  excluirfilme   : Apagar um filme do catalogo (pelo ID)");
        System.out.println("  listarusuarios : Ver todos os usuarios cadastrados");
        System.out.println("\nOutros:");
        System.out.println("  menu           : Mostrar este menu");
        System.out.println("  sair           : Encerrar o cliente");
        System.out.println("--------------------------\n");
    }

    private static boolean checarLogin(Sessao sessao) {
        if (sessao.tokenLogado == null) {
            System.out.println("  >>> ERRO: Voce precisa estar logado para executar este comando.");
            return false;
        }
        return true;
    }

    private static JsonObject enviarEAnalisar(JsonObject requisicao, PrintWriter out, BufferedReader in, Sessao sessao)
            throws IOException, JsonSyntaxException {

        if (sessao.tokenLogado != null && !requisicao.has("token")) {
            requisicao.addProperty("token", sessao.tokenLogado);
        }

        String jsonParaEnviar = gson.toJson(requisicao);

        System.out.println("\nCliente enviou: " + jsonParaEnviar);
        out.println(jsonParaEnviar);

        String respostaJson = in.readLine();

        if (respostaJson == null) {
            throw new IOException("Servidor desconectou inesperadamente.");
        }

        System.out.println("Servidor respondeu: " + respostaJson);

        JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
        String status = "???";
        String msg = "Servidor não enviou mensagem.";

        if (resposta.has("status") && resposta.get("status").isJsonPrimitive()) {
            status = resposta.get("status").getAsString();
        }
        if (resposta.has("mensagem") && resposta.get("mensagem").isJsonPrimitive()) {
            msg = resposta.get("mensagem").getAsString();
        }

        if (!status.equals("200") && !status.equals("201")) {
            System.out.println("  >>> (Status " + status + ") " + msg);
        }

        if (status.equals("401")) {
            System.out.println("  >>> Seu token expirou ou é invalido. Voce foi desconectado.");
            sessao.tokenLogado = null;
        }

        return resposta;
    }

    public static void main(String[] args) {

        Socket socket = null;
        PrintWriter out = null;
        BufferedReader in = null;
        Sessao sessao = new Sessao();

        try (BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Qual o IP do servidor? (ex: 10.20.50.27 ou localhost)");
            String serverIP = teclado.readLine();

            System.out.println("Qual a Porta do servidor? (ex: 23000)");
            int serverPort = Integer.parseInt(teclado.readLine());

            socket = new Socket(serverIP, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("Conectado ao servidor VoteFlix!");
            imprimirMenu();

            String comando;
            boolean continuar = true;

            while (continuar) {
                System.out.print("\nDigite um comando: ");
                comando = teclado.readLine();
                if (comando == null)
                    break;

                try {
                    continuar = tratarComando(comando, teclado, out, in, sessao);
                } catch (IOException e) {
                    System.err.println("  >>> ERRO DE COMUNICACAO: " + e.getMessage());
                    break;
                } catch (JsonSyntaxException e) {
                    System.err.println("  >>> ERRO: Servidor enviou JSON invalido. " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("  >>> ERRO INESPERADO no cliente: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        } catch (UnknownHostException e) {
            System.err.println("Host desconhecido: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Nao foi possivel conectar ao servidor: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erro geral: " + e.getMessage());
        } finally {
            System.out.println("Encerrando cliente...");
            try {
                if (in != null)
                    in.close();
                if (out != null)
                    out.close();
                if (socket != null)
                    socket.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar recursos: " + e.getMessage());
            }
        }
    }

    private static boolean tratarComando(String comando, BufferedReader teclado, PrintWriter out, BufferedReader in,
                                         Sessao sessao) throws IOException {

        switch (comando.toLowerCase()) {
            case "login":
                tratarLogin(teclado, out, in, sessao);
                break;
            case "criarusuario":
                tratarCriarUsuario(teclado, out, in, sessao);
                break;
            case "verperfil":
                tratarVerPerfil(out, in, sessao);
                break;
            case "editaruser":
                tratarEditarUsuario(teclado, out, in, sessao);
                break;
            case "excluiruser":
                if (tratarExcluirUsuario(teclado, out, in, sessao)) {
                    return false;
                }
                break;
            case "criarfilme":
                tratarCriarFilme(teclado, out, in, sessao);
                break;
            case "listarusuarios":
                tratarListarUsuarios(out, in, sessao);
                break;
            case "listarfilmes":
                tratarListarFilmes(out, in, sessao);
                break;
            case "buscarfilme":
                tratarBuscarFilme(teclado, out, in, sessao);
                break;
            case "excluirfilme":
                tratarExcluirFilme(teclado, out, in, sessao);
                break;
            case "editarfilme":
                tratarEditarFilme(teclado, out, in, sessao);
                break;
            case "logout":
                if (tratarLogout(out, in, sessao)) {
                    return false;
                }
                break;
            case "menu":
                imprimirMenu();
                break;
            case "sair":
                System.out.println("Saindo...");
                return false;
            default:
                tratarComandoDesconhecido(comando, out, in, sessao);
                break;
        }
        return true;
    }

    private static void tratarLogin(BufferedReader teclado, PrintWriter out, BufferedReader in, Sessao sessao)
            throws IOException {
        if (sessao.tokenLogado != null) {
            System.out.println("  >>> ERRO: Voce ja esta logado. Faca logout primeiro.");
            return;
        }

        System.out.print("  Usuario: ");
        String usuario = teclado.readLine();
        System.out.print("  Senha: ");
        String senha = teclado.readLine();

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "LOGIN");
        req.addProperty("usuario", usuario);
        req.addProperty("senha", senha);

        JsonObject resposta = enviarEAnalisar(req, out, in, sessao);
        String status = resposta.get("status").getAsString();

        if (status.equals("200")) {
            sessao.tokenLogado = resposta.get("token").getAsString();
            System.out.println("  >>> (Status 200) Login realizado com sucesso.");
        }
    }

    private static void tratarCriarUsuario(BufferedReader teclado, PrintWriter out, BufferedReader in, Sessao sessao)
            throws IOException {
        System.out.print("  Novo Usuario: ");
        String usuario = teclado.readLine();
        System.out.print("  Nova Senha: ");
        String senha = teclado.readLine();

        JsonObject usuarioObj = new JsonObject();
        usuarioObj.addProperty("nome", usuario);
        usuarioObj.addProperty("senha", senha);

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "CRIAR_USUARIO");
        req.add("usuario", usuarioObj);

        JsonObject resposta = enviarEAnalisar(req, out, in, sessao);
        if (resposta.get("status").getAsString().equals("201")) {
            System.out.println("  >>> (Status 201) Usuario criado com sucesso. Voce ja pode fazer login.");
        }
    }

    private static void tratarVerPerfil(PrintWriter out, BufferedReader in, Sessao sessao) throws IOException {
        if (!checarLogin(sessao))
            return;

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "LISTAR_PROPRIO_USUARIO");

        JsonObject resposta = enviarEAnalisar(req, out, in, sessao);
        String status = resposta.get("status").getAsString();

        if (status.equals("200")) {
            String nomeUsuario = resposta.get("usuario").getAsString();
            System.out.println("  >>> (Status 200) Usuario logado: " + nomeUsuario);
        }
    }

    private static void tratarEditarUsuario(BufferedReader teclado, PrintWriter out, BufferedReader in, Sessao sessao)
            throws IOException {
        if (!checarLogin(sessao))
            return;

        System.out.print("  Digite sua Nova Senha: ");
        String novaSenha = teclado.readLine();

        JsonObject usuarioObj = new JsonObject();
        usuarioObj.addProperty("senha", novaSenha);

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "EDITAR_PROPRIO_USUARIO");
        req.add("usuario", usuarioObj);

        JsonObject resposta = enviarEAnalisar(req, out, in, sessao);
        if (resposta.get("status").getAsString().equals("200")) {
            System.out.println("  >>> (Status 200) Senha alterada com sucesso.");
        }
    }

    private static boolean tratarExcluirUsuario(BufferedReader teclado, PrintWriter out, BufferedReader in, Sessao sessao)
            throws IOException {
        if (!checarLogin(sessao))
            return false;

        System.out.print("  Tem certeza que deseja excluir sua propria conta e todas as suas reviews? (s/N): ");
        String confirmacao = teclado.readLine();

        if (!confirmacao.equalsIgnoreCase("s")) {
            System.out.println("  >>> Operacao cancelada.");
            return false;
        }

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "EXCLUIR_PROPRIO_USUARIO");

        JsonObject resposta = enviarEAnalisar(req, out, in, sessao);
        if (resposta.get("status").getAsString().equals("200")) {
            System.out.println("  >>> (Status 200) Usuario excluido com sucesso. Desconectando...");
            sessao.tokenLogado = null;
            return true;
        }
        return false;
    }

    private static boolean tratarLogout(PrintWriter out, BufferedReader in, Sessao sessao) throws IOException {
        if (!checarLogin(sessao))
            return false;

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "LOGOUT");

        JsonObject resposta = enviarEAnalisar(req, out, in, sessao);
        if (resposta.get("status").getAsString().equals("200")) {
            System.out.println("  >>> (Status 200) Logout realizado com sucesso. Desconectando...");
            sessao.tokenLogado = null;
            return true;
        }
        return false;
    }

    private static void tratarCriarFilme(BufferedReader teclado, PrintWriter out, BufferedReader in, Sessao sessao)
            throws IOException {
        if (!checarLogin(sessao))
            return;

        System.out.print("  Titulo (min 3, max 30): ");
        String titulo = teclado.readLine();
        System.out.print("  Diretor (min 3, max 30): ");
        String diretor = teclado.readLine();
        System.out.print("  Ano (ex: 2010, min 3, max 4): ");
        String ano = teclado.readLine();
        System.out.print("  Generos (separados por virgula, ex: Ação, Comédia): ");
        String generosInput = teclado.readLine();
        System.out.print("  Sinopse (max 250): ");
        String sinopse = teclado.readLine();

        JsonArray generosJson = new JsonArray();
        for (String g : generosInput.split(",")) {
            if (!g.trim().isEmpty()) {
                generosJson.add(g.trim());
            }
        }

        JsonObject filmeObj = new JsonObject();
        filmeObj.addProperty("titulo", titulo);
        filmeObj.addProperty("diretor", diretor);
        filmeObj.addProperty("ano", ano);
        filmeObj.add("genero", generosJson);
        filmeObj.addProperty("sinopse", sinopse);

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "CRIAR_FILME");
        req.add("filme", filmeObj);

        JsonObject resposta = enviarEAnalisar(req, out, in, sessao);
        if (resposta.get("status").getAsString().equals("201")) {
            System.out.println("  >>> (Status 201) Filme criado com sucesso.");
        }
    }

    private static void tratarListarUsuarios(PrintWriter out, BufferedReader in, Sessao sessao) throws IOException {
        if (!checarLogin(sessao))
            return;

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "LISTAR_USUARIOS");

        JsonObject resposta = enviarEAnalisar(req, out, in, sessao);
        String status = resposta.get("status").getAsString();

        if (status.equals("200")) {
            System.out.println("\n--- Lista de Usuarios ---");
            JsonArray usuarios = resposta.getAsJsonArray("usuarios");

            if (usuarios.size() == 0) {
                System.out.println("  (Nenhum usuario encontrado)");
            }
            for (JsonElement userElement : usuarios) {
                JsonObject userObj = userElement.getAsJsonObject();
                String id = userObj.get("id").getAsString();
                String nome = userObj.get("nome").getAsString();
                System.out.println("  [" + id + "] " + nome);
            }
            System.out.println("---------------------------");
        }
    }

    private static void tratarListarFilmes(PrintWriter out, BufferedReader in, Sessao sessao) throws IOException {
        if (!checarLogin(sessao))
            return;

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "LISTAR_FILMES");

        JsonObject resposta = enviarEAnalisar(req, out, in, sessao);
        String status = resposta.get("status").getAsString();

        if (status.equals("200")) {
            System.out.println("\n--- Catalogo de Filmes ---");
            JsonArray filmes = resposta.getAsJsonArray("filmes");

            if (filmes.size() == 0) {
                System.out.println("  Nenhum filme cadastrado ainda.");
            }

            for (JsonElement filmeElement : filmes) {
                JsonObject filmeObj = filmeElement.getAsJsonObject();
                String id = filmeObj.get("id").getAsString();
                String titulo = filmeObj.get("titulo").getAsString();
                String ano = filmeObj.get("ano").getAsString();
                String nota = filmeObj.get("nota").getAsString();
                String qtd = filmeObj.get("qtd_avaliacoes").getAsString();

                System.out.println("\n  [" + id + "] " + titulo + " (" + ano + ")");
                System.out.println("     Nota: " + nota + " (" + qtd + " avaliacoes)");
            }
            System.out.println("\n----------------------------");
        }
    }

    private static void tratarBuscarFilme(BufferedReader teclado, PrintWriter out, BufferedReader in, Sessao sessao)
            throws IOException {
        if (!checarLogin(sessao))
            return;

        System.out.print("  ID do filme a buscar: ");
        String idFilme = teclado.readLine();

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "BUSCAR_FILME_ID");
        req.addProperty("id_filme", idFilme);

        JsonObject resposta = enviarEAnalisar(req, out, in, sessao);
        String status = resposta.get("status").getAsString();

        if (status.equals("200")) {
            JsonObject filme = resposta.getAsJsonObject("filme");
            System.out.println("\n--- Detalhes do Filme ---");
            System.out.println(
                    "  Titulo: " + filme.get("titulo").getAsString() + " (" + filme.get("ano").getAsString() + ")");
            System.out.println("  Diretor: " + filme.get("diretor").getAsString());
            System.out.println("  Generos: " + filme.get("genero").toString());
            System.out.println("  Nota Media: " + filme.get("nota").getAsString() + " (de "
                    + filme.get("qtd_avaliacoes").getAsString() + " avaliações)");
            System.out.println("  Sinopse: " + filme.get("sinopse").getAsString());

            System.out.println("\n--- Reviews ---");
            JsonArray reviews = resposta.getAsJsonArray("reviews");
            if (reviews.size() == 0) {
                System.out.println("  (Nenhuma review para este filme ainda)");
            } else {
                for (JsonElement reviewElement : reviews) {
                    JsonObject r = reviewElement.getAsJsonObject();
                    System.out.println("\n  Usuario: " + r.get("nome_usuario").getAsString() + " (Nota: "
                            + r.get("nota").getAsString() + ")");
                    System.out.println("  Em: " + r.get("data").getAsString());
                    System.out.println("  Titulo: " + r.get("titulo").getAsString());
                    System.out.println("  " + r.get("descricao").getAsString());
                }
            }
            System.out.println("-------------------------");
        }
    }

    private static void tratarExcluirFilme(BufferedReader teclado, PrintWriter out, BufferedReader in, Sessao sessao)
            throws IOException {
        if (!checarLogin(sessao))
            return;

        System.out.print("  ID do filme a excluir: ");
        String idFilme = teclado.readLine();

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "EXCLUIR_FILME");
        req.addProperty("id", idFilme);

        JsonObject resposta = enviarEAnalisar(req, out, in, sessao);
        if (resposta.get("status").getAsString().equals("200")) {
            System.out.println("  >>> (Status 200) Filme excluido com sucesso.");
        }
    }

    private static void tratarEditarFilme(BufferedReader teclado, PrintWriter out, BufferedReader in, Sessao sessao)
            throws IOException {
        if (!checarLogin(sessao))
            return;

        System.out.print("  ID do filme a editar: ");
        String idFilme = teclado.readLine();
        System.out.print("  Novo Titulo (min 3, max 30): ");
        String titulo = teclado.readLine();
        System.out.print("  Novo Diretor (min 3, max 30): ");
        String diretor = teclado.readLine();
        System.out.print("  Novo Ano (ex: 2010, min 3, max 4): ");
        String ano = teclado.readLine();
        System.out.print("  Novos Generos (separados por virgula): ");
        String generosInput = teclado.readLine();
        System.out.print("  Nova Sinopse (max 250): ");
        String sinopse = teclado.readLine();

        JsonArray generosJson = new JsonArray();
        for (String g : generosInput.split(",")) {
            if (!g.trim().isEmpty()) {
                generosJson.add(g.trim());
            }
        }

        JsonObject filmeObj = new JsonObject();
        filmeObj.addProperty("id", idFilme);
        filmeObj.addProperty("titulo", titulo);
        filmeObj.addProperty("diretor", diretor);
        filmeObj.addProperty("ano", ano);
        filmeObj.add("genero", generosJson);
        filmeObj.addProperty("sinopse", sinopse);

        JsonObject req = new JsonObject();
        req.addProperty("operacao", "EDITAR_FILME");
        req.add("filme", filmeObj);

        JsonObject resposta = enviarEAnalisar(req, out, in, sessao);
        if (resposta.get("status").getAsString().equals("200")) {
            System.out.println("  >>> (Status 200) Filme editado com sucesso.");
        }
    }

    private static void tratarComandoDesconhecido(String comando, PrintWriter out, BufferedReader in, Sessao sessao)
            throws IOException {
        System.out.println(">>> Comando desconhecido. Tentando enviar '" + comando + "' para o servidor...");
        JsonObject req = new JsonObject();
        req.addProperty("operacao", comando);
        enviarEAnalisar(req, out, in, sessao);
    }
}