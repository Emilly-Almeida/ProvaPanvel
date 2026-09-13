import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiListaCompras {
    private static final int PORTA = 8081;
    private static final List<Produto> PRODUTOS =
            Collections.synchronizedList(new ArrayList<Produto>());

    public static void main(String[] args) throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(PORTA), 0);
        servidor.createContext("/produtos", new ProdutosHandler());
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.start();

        System.out.println("API iniciada em http://localhost:" + PORTA);
        System.out.println("Pressione Ctrl+C para encerrar.");
    }

    private static class ProdutosHandler implements HttpHandler {
        private static final Pattern CAMPO_NOME = Pattern.compile(
                "\\\"nome\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"",
                Pattern.DOTALL
        );

        @Override
        public void handle(HttpExchange requisicao) throws IOException {
            try {
                String metodo = requisicao.getRequestMethod().toUpperCase();
                String caminho = normalizarCaminho(requisicao.getRequestURI().getRawPath());

                if ("/produtos".equals(caminho)) {
                    if ("POST".equals(metodo)) {
                        adicionarProduto(requisicao);
                    } else if ("GET".equals(metodo)) {
                        listarProdutos(requisicao);
                    } else {
                        metodoNaoPermitido(requisicao);
                    }
                    return;
                }

                if ("/produtos/total".equals(caminho)) {
                    if ("GET".equals(metodo)) {
                        retornarTotal(requisicao);
                    } else {
                        metodoNaoPermitido(requisicao);
                    }
                    return;
                }

                String[] partes = caminho.split("/");

                if (partes.length == 3 && "produtos".equals(partes[1])) {
                    if ("DELETE".equals(metodo)) {
                        removerProduto(requisicao, decodificar(partes[2]));
                    } else {
                        metodoNaoPermitido(requisicao);
                    }
                    return;
                }

                if (partes.length == 4
                        && "produtos".equals(partes[1])
                        && "comprado".equals(partes[3])) {
                    if ("PUT".equals(metodo)) {
                        marcarComoComprado(requisicao, decodificar(partes[2]));
                    } else {
                        metodoNaoPermitido(requisicao);
                    }
                    return;
                }

                responderJson(requisicao, 404,
                        "{\"erro\":\"Endpoint não encontrado\"}");
            } catch (Exception erro) {
                responderJson(requisicao, 500,
                        "{\"erro\":\"Erro interno da API\"}");
            }
        }

        private void adicionarProduto(HttpExchange requisicao) throws IOException {
            String corpo = lerCorpo(requisicao);
            Matcher matcher = CAMPO_NOME.matcher(corpo);

            if (!matcher.find()) {
                responderJson(requisicao, 400,
                        "{\"erro\":\"Informe o produto no formato {\\\"nome\\\":\\\"Arroz\\\"}\"}");
                return;
            }

            String nome = desescaparJson(matcher.group(1)).trim();

            if (nome.isEmpty()) {
                responderJson(requisicao, 400,
                        "{\"erro\":\"O nome do produto não pode estar vazio\"}");
                return;
            }

            Produto produto = new Produto(nome);
            PRODUTOS.add(produto);
            responderJson(requisicao, 201, produto.paraJson());
        }

        private void listarProdutos(HttpExchange requisicao) throws IOException {
            StringBuilder json = new StringBuilder("[");

            synchronized (PRODUTOS) {
                for (int i = 0; i < PRODUTOS.size(); i++) {
                    if (i > 0) {
                        json.append(',');
                    }
                    json.append(PRODUTOS.get(i).paraJson());
                }
            }

            json.append(']');
            responderJson(requisicao, 200, json.toString());
        }

        private void removerProduto(HttpExchange requisicao, String nome)
                throws IOException {
            Produto removido = null;

            synchronized (PRODUTOS) {
                for (int i = 0; i < PRODUTOS.size(); i++) {
                    if (PRODUTOS.get(i).getNome().equalsIgnoreCase(nome)) {
                        removido = PRODUTOS.remove(i);
                        break;
                    }
                }
            }

            if (removido == null) {
                produtoNaoEncontrado(requisicao, nome);
                return;
            }

            responderJson(requisicao, 200,
                    "{\"mensagem\":\"Produto removido\",\"produto\":"
                            + removido.paraJson() + "}");
        }

        private void marcarComoComprado(HttpExchange requisicao, String nome)
                throws IOException {
            Produto encontrado = null;

            synchronized (PRODUTOS) {
                for (Produto produto : PRODUTOS) {
                    if (produto.getNome().equalsIgnoreCase(nome)) {
                        produto.setComprado(true);
                        encontrado = produto;
                        break;
                    }
                }
            }

            if (encontrado == null) {
                produtoNaoEncontrado(requisicao, nome);
                return;
            }

            responderJson(requisicao, 200, encontrado.paraJson());
        }

        private void retornarTotal(HttpExchange requisicao) throws IOException {
            responderJson(requisicao, 200,
                    "{\"total\":" + PRODUTOS.size() + "}");
        }

        private void produtoNaoEncontrado(HttpExchange requisicao, String nome)
                throws IOException {
            responderJson(requisicao, 404,
                    "{\"erro\":\"Produto não encontrado\",\"produto\":\""
                            + escaparJson(nome) + "\"}");
        }

        private void metodoNaoPermitido(HttpExchange requisicao) throws IOException {
            responderJson(requisicao, 405,
                    "{\"erro\":\"Método não permitido para este endpoint\"}");
        }
    }

    private static class Produto {
        private final String nome;
        private boolean comprado;

        Produto(String nome) {
            this.nome = nome;
            this.comprado = false;
        }

        String getNome() {
            return nome;
        }

        void setComprado(boolean comprado) {
            this.comprado = comprado;
        }

        String paraJson() {
            return "{\"nome\":\"" + escaparJson(nome)
                    + "\",\"comprado\":" + comprado + "}";
        }
    }

    private static String normalizarCaminho(String caminho) {
        if (caminho.length() > 1 && caminho.endsWith("/")) {
            return caminho.substring(0, caminho.length() - 1);
        }
        return caminho;
    }

    private static String decodificar(String valor) throws IOException {
        return URLDecoder.decode(valor, StandardCharsets.UTF_8.name());
    }

    private static String lerCorpo(HttpExchange requisicao) throws IOException {
        StringBuilder corpo = new StringBuilder();

        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(
                requisicao.getRequestBody(), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                corpo.append(linha);
            }
        }

        return corpo.toString();
    }

    private static void responderJson(HttpExchange requisicao, int status, String json)
            throws IOException {
        byte[] resposta = json.getBytes(StandardCharsets.UTF_8);
        requisicao.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");
        requisicao.sendResponseHeaders(status, resposta.length);

        try (OutputStream saida = requisicao.getResponseBody()) {
            saida.write(resposta);
        }
    }

    private static String escaparJson(String texto) {
        StringBuilder resultado = new StringBuilder();

        for (int i = 0; i < texto.length(); i++) {
            char caractere = texto.charAt(i);

            switch (caractere) {
                case '\"': resultado.append("\\\""); break;
                case '\\': resultado.append("\\\\"); break;
                case '\b': resultado.append("\\b"); break;
                case '\f': resultado.append("\\f"); break;
                case '\n': resultado.append("\\n"); break;
                case '\r': resultado.append("\\r"); break;
                case '\t': resultado.append("\\t"); break;
                default:
                    if (caractere < 32) {
                        resultado.append(String.format("\\u%04x", (int) caractere));
                    } else {
                        resultado.append(caractere);
                    }
            }
        }

        return resultado.toString();
    }

    private static String desescaparJson(String texto) {
        StringBuilder resultado = new StringBuilder();

        for (int i = 0; i < texto.length(); i++) {
            char caractere = texto.charAt(i);

            if (caractere != '\\' || i + 1 >= texto.length()) {
                resultado.append(caractere);
                continue;
            }

            char escapado = texto.charAt(++i);
            switch (escapado) {
                case '\"': resultado.append('\"'); break;
                case '\\': resultado.append('\\'); break;
                case '/': resultado.append('/'); break;
                case 'b': resultado.append('\b'); break;
                case 'f': resultado.append('\f'); break;
                case 'n': resultado.append('\n'); break;
                case 'r': resultado.append('\r'); break;
                case 't': resultado.append('\t'); break;
                case 'u':
                    if (i + 4 < texto.length()) {
                        String hexadecimal = texto.substring(i + 1, i + 5);
                        try {
                            resultado.append((char) Integer.parseInt(hexadecimal, 16));
                            i += 4;
                        } catch (NumberFormatException erro) {
                            resultado.append("\\u").append(hexadecimal);
                            i += 4;
                        }
                    } else {
                        resultado.append("\\u");
                    }
                    break;
                default: resultado.append(escapado);
            }
        }

        return resultado.toString();
    }
}
