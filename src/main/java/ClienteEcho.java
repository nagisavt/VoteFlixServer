import java.net.*;
import java.io.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class ClienteEcho {

    public static void main(String[] args) throws IOException {

        Socket socket = null;
        PrintWriter out = null;
        BufferedReader in = null;
        Gson gson = new Gson();

        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

        try {
            System.out.println("Qual o IP do servidor? (ex: 10.20.50.27 ou localhost)");
            String serverIP = teclado.readLine();

            System.out.println("Qual a Porta do servidor? (ex: 23000)");
            int serverPort = Integer.parseInt(teclado.readLine());

            socket = new Socket(serverIP, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("Conectado ao servidor VoteFlix!");
            System.out.println("\n--- Comandos Disponiveis ---");
            System.out.println("Gerais:");
            System.out.println("  login          : Entrar no sistema");
            System.out.println("  criarusuario   : Registrar uma nova conta");
            System.out.println("\nUsuario Logado:");
            //System.out.println("  listarfilmes   : Ver todos os filmes cadastrados");
            //System.out.println("  buscarfilme    : Ver detalhes e reviews de um filme (pelo ID)");
            //System.out.println("  criarreview    : Adicionar uma review a um filme");
            //System.out.println("  minhasreviews  : Listar todas as suas reviews");
            //System.out.println("  editarreview   : Modificar uma de suas reviews (pelo ID da review)");
            //System.out.println("  excluirreview  : Apagar uma de suas reviews (pelo ID da review)");
            System.out.println("  verperfil      : Ver o nome do seu usuario");
            System.out.println("  excluiruser    : Apagar sua propria conta");
            System.out.println("  editaruser    : Editar sua propria conta"); // Corrigi a descrição
            System.out.println("  logout         : Sair da conta atual");
            //System.out.println("\nAdministrador:");
            //... (comandos admin)
            System.out.println("\n--------------------------\n");

            String tokenLogado = null;
            String comando;

            while (true) {
                System.out.print("Digite um comando: ");
                comando = teclado.readLine();

                String jsonRequisicao = "";
                String respostaJson = "";

                if (comando.equalsIgnoreCase("login")) {
                    if (tokenLogado != null) {
                        System.out.println("  >>> ERRO: Voce ja esta logado. Faca logout primeiro.");
                        continue;
                    }

                    System.out.print("  Usuario: ");
                    String usuario = teclado.readLine();
                    System.out.print("  Senha: ");
                    String senha = teclado.readLine();

                    jsonRequisicao = "{\"operacao\": \"LOGIN\", \"usuario\": \"" + usuario + "\", \"senha\": \"" + senha + "\"}";
                    out.println(jsonRequisicao);
                    respostaJson = in.readLine();

                    if (respostaJson == null) {
                        System.out.println("  >>> ERRO: Servidor desconectou inesperadamente.");
                        break;
                    }

                    JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
                    String status = resposta.get("status").getAsString();
                    String msg = "";

                    if (resposta.has("mensagem")) {
                        msg = resposta.get("mensagem").getAsString();
                    } else {
                        msg = "Servidor não enviou mensagem.";
                    }
                    
                    System.out.println("  >>> (Status " + status + ") " + msg);

                    if (status.equals("200")) {
                        tokenLogado = resposta.get("token").getAsString();
                    }

                } else if (comando.equalsIgnoreCase("criarusuario")) {
                    System.out.print("  Novo Usuario: ");
                    String usuario = teclado.readLine();
                    System.out.print("  Nova Senha: ");
                    String senha = teclado.readLine();

                    jsonRequisicao = "{\"operacao\": \"CRIAR_USUARIO\", \"usuario\": {\"nome\": \"" + usuario + "\", \"senha\": \"" + senha + "\"}}";
                    out.println(jsonRequisicao);
                    respostaJson = in.readLine();

                    if (respostaJson == null) {
                        System.out.println("  >>> ERRO: Servidor desconectou inesperadamente.");
                        break;
                    }

                    JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
                    String status = resposta.get("status").getAsString();
                    String msg = "";

                    if (resposta.has("mensagem")) {
                        msg = resposta.get("mensagem").getAsString();
                    } else {
                        msg = "Servidor não enviou mensagem.";
                    }

                    System.out.println("  >>> (Status " + status + ") " + msg);

                } else if (comando.equalsIgnoreCase("verperfil")) {
                    if (tokenLogado == null) {
                        System.out.println("  >>> ERRO: Voce precisa estar logado.");
                        continue;
                    }

                    jsonRequisicao = String.format(
                            "{\"operacao\": \"LISTAR_PROPRIO_USUARIO\", \"token\": \"%s\"}",
                            tokenLogado
                    );

                    out.println(jsonRequisicao);
                    respostaJson = in.readLine();

                    if (respostaJson == null) {
                        System.out.println("  >>> ERRO: Servidor desconectou inesperadamente.");
                        break;
                    }

                    JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
                    String status = resposta.get("status").getAsString();
                    String msg = "";

                    if (resposta.has("mensagem")) {
                        msg = resposta.get("mensagem").getAsString();
                    } else {
                        msg = "Servidor não enviou mensagem.";
                    }

                    if (status.equals("200")) {
                        String nomeUsuario = resposta.get("usuario").getAsString();
                        System.out.println("  >>> (Status " + status + ") " + msg + " (Usuario: " + nomeUsuario + ")");
                    } else {
                        System.out.println("  >>> (Status " + status + ") " + msg);
                    }

                    if (status.equals("401")) {
                        tokenLogado = null;
                    }

                } else if (comando.equalsIgnoreCase("editaruser")) {
                    if (tokenLogado == null) {
                        System.out.println("  >>> ERRO: Voce precisa estar logado.");
                        continue;
                    }

                    System.out.print("  Digite sua Nova Senha: ");
                    String novaSenha = teclado.readLine();

                    jsonRequisicao = String.format(
                            "{\"operacao\": \"EDITAR_PROPRIO_USUARIO\", \"token\": \"%s\", \"usuario\": {\"senha\": \"%s\"}}",
                            tokenLogado, novaSenha
                    );

                    out.println(jsonRequisicao);
                    respostaJson = in.readLine();

                    if (respostaJson == null) {
                        System.out.println("  >>> ERRO: Servidor desconectou inesperadamente.");
                        break;
                    }

                    JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
                    String status = resposta.get("status").getAsString();
                    String msg = "";

                    if (resposta.has("mensagem")) {
                        msg = resposta.get("mensagem").getAsString();
                    } else {
                        msg = "Servidor não enviou mensagem.";
                    }

                    System.out.println("  >>> (Status " + status + ") " + msg);

                    if (status.equals("401")) {
                        tokenLogado = null;
                    }

                } else if (comando.equalsIgnoreCase("listarusuarios")) {
                    if (tokenLogado == null) {
                        System.out.println("  >>> ERRO: Voce precisa estar logado.");
                        continue;
                    }

                    jsonRequisicao = String.format(
                            "{\"operacao\": \"LISTAR_USUARIOS\", \"token\": \"%s\"}",
                            tokenLogado
                    );

                    out.println(jsonRequisicao);
                    respostaJson = in.readLine();

                    if (respostaJson == null) {
                        System.out.println("  >>> ERRO: Servidor desconectou inesperadamente.");
                        break;
                    }

                    JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
                    String status = resposta.get("status").getAsString();

                    if (status.equals("200")) {
                        System.out.println("  >>> Lista de Usuarios do Sistema:");
                        JsonArray usuariosArray = resposta.get("usuarios").getAsJsonArray();

                        for (JsonElement userEl : usuariosArray) {
                            JsonObject user = userEl.getAsJsonObject();
                            int id = user.get("id").getAsInt();
                            String nome = user.get("nome").getAsString();
                            System.out.println(String.format("    [%d] %s", id, nome));
                        }
                    } else if (status.equals("401")) {
                        System.out.println("  >>> Falha ao listar usuarios: Acesso negado. Apenas administradores.");
                    } else {
                        System.out.println("  >>> Falha ao listar usuarios (Status: " + status + ").");
                    }


                } else if (comando.equalsIgnoreCase("excluiruser")) {
                    if (tokenLogado == null) {
                        System.out.println("  >>> ERRO: Voce precisa estar logado.");
                        continue;
                    }

                    System.out.print("  Tem certeza que deseja excluir sua propria conta e todas as suas reviews? (s/N): ");
                    String confirmacao = teclado.readLine();

                    if (!confirmacao.equalsIgnoreCase("s")) {
                        System.out.println("  >>> Operacao cancelada.");
                        continue;
                    }

                    jsonRequisicao = String.format(
                            "{\"operacao\": \"EXCLUIR_PROPRIO_USUARIO\", \"token\": \"%s\"}",
                            tokenLogado
                    );

                    out.println(jsonRequisicao);
                    respostaJson = in.readLine();

                    if (respostaJson == null) {
                        System.out.println("  >>> ERRO: Servidor desconectou inesperadamente.");
                        break;
                    }

                    JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
                    String status = resposta.get("status").getAsString();
                    String msg = "";

                    if (resposta.has("mensagem")) {
                        msg = resposta.get("mensagem").getAsString();
                    } else {
                        msg = "Servidor não enviou mensagem.";
                    }

                    System.out.println("  >>> (Status " + status + ") " + msg);

                    if (status.equals("200")) {
                        System.out.println("  >>> Voce foi desconectado.");
                        tokenLogado = null;
                        break;
                    } else if (status.equals("401")) {
                        tokenLogado = null;
                    }

                } else if (comando.equalsIgnoreCase("logout")) {
                    if (tokenLogado == null) {
                        System.out.println("  >>> ERRO: Voce nao esta logado.");
                        continue;
                    }

                    jsonRequisicao = "{\"operacao\": \"LOGOUT\", \"token\": \"" + tokenLogado + "\"}";
                    out.println(jsonRequisicao);
                    respostaJson = in.readLine();

                    if (respostaJson == null) {
                        System.out.println("  >>> ERRO: Servidor desconectou inesperadamente.");
                        break;
                    }

                    JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
                    String status = resposta.get("status").getAsString();
                    String msg = "";

                    if (resposta.has("mensagem")) {
                        msg = resposta.get("mensagem").getAsString();
                    } else {
                        msg = "Servidor não enviou mensagem.";
                    }

                    System.out.println("  >>> (Status " + status + ") " + msg);

                    tokenLogado = null;

                    if (status.equals("200")) {
                        System.out.println("  >>> Desconectando...");
                        break;
                    }

                } else {

                    if (comando.equalsIgnoreCase("sair")) {
                        System.out.println("Saindo...");
                        break;
                    }

                    System.out.println(">>> Enviando comando desconhecido '" + comando + "' para o servidor...");

                    if (tokenLogado != null) {
                        jsonRequisicao = String.format(
                                "{\"operacao\": \"%s\", \"token\": \"%s\"}",
                                comando, tokenLogado
                        );
                    } else {
                        jsonRequisicao = "{\"operacao\": \"" + comando + "\"}";
                    }

                    out.println(jsonRequisicao);
                    respostaJson = in.readLine();

                    if (respostaJson == null) {
                        System.out.println("  >>> ERRO: Servidor desconectou inesperadamente.");
                        continue;
                    }

                    try {
                        JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
                        String status = resposta.get("status").getAsString();
                        String msg = "Mensagem desconhecida";
                        if (resposta.has("mensagem")) {
                            msg = resposta.get("mensagem").getAsString();
                        }

                        System.out.println("  >>> (Status " + status + ") " + msg);

                    } catch (Exception e) {
                        System.out.println("  >>> Servidor enviou resposta mal formatada: " + respostaJson);
                    }
                }
            }

        } catch (UnknownHostException e) {
            System.err.println("Host desconhecido: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Erro de E/S ao conectar: " + e.getMessage());
            System.exit(1);
        } finally {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
            if (teclado != null) teclado.close();
            System.out.println("Conexao fechada.");
        }
    }
}