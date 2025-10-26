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
            System.out.println("  sair           : Fechar o programa");

            System.out.println("\nUsuario Logado:");
            //System.out.println("  listarfilmes   : Ver todos os filmes cadastrados");
            //System.out.println("  buscarfilme    : Ver detalhes e reviews de um filme (pelo ID)");
            //System.out.println("  criarreview    : Adicionar uma review a um filme");
            //System.out.println("  minhasreviews  : Listar todas as suas reviews");
            //System.out.println("  editarreview   : Modificar uma de suas reviews (pelo ID da review)");
            //System.out.println("  excluirreview  : Apagar uma de suas reviews (pelo ID da review)");
            System.out.println("  verperfil      : Ver o nome do seu usuario");
            System.out.println("  excluiruser    : Apagar sua propria conta");
            System.out.println("  editaruser    : Apagar sua propria conta");
            System.out.println("  logout         : Sair da conta atual");

            //System.out.println("\nAdministrador:");
            //System.out.println("  criarfilme     : Adicionar um novo filme");
            //System.out.println("  editarfilme    : Modificar dados de um filme (pelo ID do filme)");
            //System.out.println("  excluirfilme   : Apagar um filme (pelo ID do filme)");
            //System.out.println("  listarusuarios : Ver todos os usuarios do sistema");
            //System.out.println("  admineditaruser: Mudar a senha de um usuario (pelo ID do usuario)");
            //System.out.println("  adminexcluiruser: Apagar um usuario (pelo ID do usuario)");
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

                    JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
                    String status = resposta.get("status").getAsString();
                    String msg = "";

                    if (resposta.has("mensagem")) {
                        msg = resposta.get("mensagem").getAsString();
                    } else {
                        msg = "Servidor não enviou mensagem.";
                    }

                    if (status.equals("200")) {
                        tokenLogado = resposta.get("token").getAsString();
                        System.out.println("  >>> " + msg); // "Sucesso: operação realizada com sucesso"
                    } else if (status.equals("422")) {
                        System.out.println("  >>> " + msg); // "Erro: Chaves faltantes ou invalidas"
                    } else if (status.equals("500")) {
                        System.out.println("  >>> " + msg); // "Erro: Falha interna do servidor"
                    } else if (status.equals("403")) {
                        System.out.println("  >>> " + msg); // "Erro: sem permissão"
                    } else {
                        // Pega qualquer outro erro, como o 400 "Credenciais inválidas"
                        System.out.println("  >>> Falha no login. (Status: " + status + "): " + msg);
                    }

                } else if (comando.equalsIgnoreCase("criarusuario")) {
                    System.out.print("  Novo Usuario: ");
                    String usuario = teclado.readLine();
                    System.out.print("  Nova Senha: ");
                    String senha = teclado.readLine();

                    jsonRequisicao = "{\"operacao\": \"CRIAR_USUARIO\", \"usuario\": {\"nome\": \"" + usuario + "\", \"senha\": \"" + senha + "\"}}";
                    out.println(jsonRequisicao);
                    respostaJson = in.readLine();

                    JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
                    String status = resposta.get("status").getAsString();
                    String msg = "";

                    if (resposta.has("mensagem")) {
                        msg = resposta.get("mensagem").getAsString();
                    } else {
                        msg = "Servidor não enviou mensagem.";
                    }

                    if (status.equals("201")) {
                        System.out.println("  >>> " + msg); // "Sucesso: Recurso cadastrado"
                    } else if (status.equals("409")) {
                        System.out.println("  >>> " + msg); // "Erro: Recurso ja existe"
                    } else if (status.equals("422")) {
                        System.out.println("  >>> " + msg); // "Erro: Chaves faltantes ou invalidas"
                    } else if (status.equals("405")) {
                        System.out.println("  >>> " + msg); // "Erro: Campos inválidos..."
                    } else if (status.equals("500")) {
                        System.out.println("  >>> " + msg); // "Erro: Falha interna do servidor"
                    } else if (status.equals("401")) {
                        System.out.println("  >>> " + msg); // "Erro: Token inválido" (improvável aqui)
                    } else if (status.equals("400")) {
                        System.out.println("  >>> " + msg); // "Erro: Operação não encontrada..." (improvável aqui)
                    } else {
                        // Pega qualquer outro status não listado
                        System.out.println("  >>> Falha ao criar usuario (Status: " + status + "): " + msg);
                    }

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
                        System.out.println("  >>> " + msg + " (Usuario: " + nomeUsuario + ")");
                    } else if (status.equals("401")) {
                        System.out.println("  >>> " + msg); // "Erro: Token inválido"
                        tokenLogado = null; // Limpa o token inválido do cliente
                    } else if (status.equals("404")) {
                        System.out.println("  >>> " + msg); // "Erro: Recurso inexistente"
                    } else if (status.equals("403")) {
                        System.out.println("  >>> " + msg); // "Erro: sem permissão"
                    } else if (status.equals("422")) {
                        System.out.println("  >>> " + msg); // "Erro: Chaves faltantes ou invalidas"
                    } else if (status.equals("500")) {
                        System.out.println("  >>> " + msg); // "Erro: Falha interna do servidor"
                    } else if (status.equals("400")) {
                        System.out.println("  >>> " + msg); // "Erro: Operação não encontrada..."
                    } else {
                        System.out.println("  >>> Falha ao buscar perfil (Status: " + status + "): " + msg);
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

                    JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
                    String status = resposta.get("status").getAsString();
                    String msg = "";

                    if (resposta.has("mensagem")) {
                        msg = resposta.get("mensagem").getAsString();
                    } else {
                        msg = "Servidor não enviou mensagem.";
                    }

                    if (status.equals("200")) {
                        System.out.println("  >>> " + msg); // "Sucesso: operação realizada com sucesso"
                    } else if (status.equals("401")) {
                        System.out.println("  >>> " + msg); // "Erro: Token inválido"
                        tokenLogado = null; // Limpa o token inválido
                    } else if (status.equals("405")) {
                        System.out.println("  >>> " + msg); // "Erro: Campos inválidos..." (senha muito curta)
                    } else if (status.equals("404")) {
                        System.out.println("  >>> " + msg); // "Erro: Recurso inexistente" (usuário não encontrado)
                    } else if (status.equals("422")) {
                        System.out.println("  >>> " + msg); // "Erro: Chaves faltantes ou invalidas"
                    } else if (status.equals("403")) {
                        System.out.println("  >>> " + msg); // "Erro: sem permissão"
                    } else if (status.equals("500")) {
                        System.out.println("  >>> " + msg); // "Erro: Falha interna do servidor"
                    } else if (status.equals("400")) {
                        System.out.println("  >>> " + msg); // "Erro: Operação não encontrada..."
                    } else {
                        System.out.println("  >>> Falha ao editar sua senha (Status " + status + "): " + msg);
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

                    JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
                    String status = resposta.get("status").getAsString();
                    String msg = "";

                    if (resposta.has("mensagem")) {
                        msg = resposta.get("mensagem").getAsString();
                    } else {
                        msg = "Servidor não enviou mensagem.";
                    }

                    if (status.equals("200")) {
                        System.out.println("  >>> " + msg); // "Sucesso: operação realizada com sucesso"
                        System.out.println("  >>> Voce foi desconectado.");
                        tokenLogado = null;
                        break; // Sai do loop while(true) pois o usuário foi excluído
                    } else if (status.equals("401")) {
                        System.out.println("  >>> " + msg); // "Erro: Token inválido"
                        tokenLogado = null; // Limpa o token inválido
                    } else if (status.equals("404")) {
                        System.out.println("  >>> " + msg); // "Erro: Recurso inexistente"
                    } else if (status.equals("422")) {
                        System.out.println("  >>> " + msg); // "Erro: Chaves faltantes ou invalidas"
                    } else if (status.equals("403")) {
                        System.out.println("  >>> " + msg); // "Erro: sem permissão"
                    } else if (status.equals("500")) {
                        System.out.println("  >>> " + msg); // "Erro: Falha interna do servidor"
                    } else if (status.equals("400")) {
                        System.out.println("  >>> " + msg); // "Erro: Operação não encontrada..."
                    } else {
                        System.out.println("  >>> Falha ao excluir sua conta (Status " + status + "): " + msg);
                    }

                } else if (comando.equalsIgnoreCase("logout")) {
                    if (tokenLogado == null) {
                        System.out.println("  >>> ERRO: Voce nao esta logado.");
                        continue;
                    }

                    jsonRequisicao = "{\"operacao\": \"LOGOUT\", \"token\": \"" + tokenLogado + "\"}";
                    out.println(jsonRequisicao);
                    respostaJson = in.readLine();

                    JsonObject resposta = gson.fromJson(respostaJson, JsonObject.class);
                    String status = resposta.get("status").getAsString();
                    String msg = "";

                    if (resposta.has("mensagem")) {
                        msg = resposta.get("mensagem").getAsString();
                    } else {
                        msg = "Servidor não enviou mensagem.";
                    }

                    if (status.equals("200")) {
                        tokenLogado = null;
                        System.out.println("  >>> " + msg); // "Sucesso: operação realizada com sucesso"
                        System.out.println("  >>> Desconectando...");
                        break;
                    } else if (status.equals("401")) {
                        System.out.println("  >>> " + msg); // "Erro: Token inválido"
                        // Se o token é inválido, limpamos ele do cliente
                        tokenLogado = null;
                    } else if (status.equals("404")) {
                        System.out.println("  >>> " + msg); // "Erro: Recurso inexistente"
                    } else if (status.equals("422")) {
                        System.out.println("  >>> " + msg); // "Erro: Chaves faltantes ou invalidas"
                    } else if (status.equals("500")) {
                        System.out.println("  >>> " + msg); // "Erro: Falha interna do servidor"
                    } else {
                        System.out.println("  >>> Falha ao fazer logout (Status " + status + "): " + msg);
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