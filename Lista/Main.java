import java.util.ArrayList;
import java.util.List;

class ListaCompras {
    private final List<String> lista = new ArrayList<>();

    public void adicionar(String item) {
        lista.add(item);
    }

    public void remover(String item) {
        lista.remove(item);
    }

    public List<String> listar() {
        return new ArrayList<>(lista);
    }

    public void marcar_comprado(String item) {
        int posicao = lista.indexOf(item);

        if (posicao != -1) {
            lista.set(posicao, item + "comprado");
        } else {
            System.out.println("Nao encontrado.");
        }
    }

    public int total() {
        return lista.size();
    }
}

public class Main {
    public static void main(String[] args) {
        ListaCompras lista = new ListaCompras();

        lista.adicionar("Arroz");
        lista.adicionar("Feijão");
        lista.adicionar("Leite");

        lista.marcar_comprado("Arroz");
        lista.remover("Leite");

        for (String produto : lista.listar()) {
            System.out.println(produto);
        }

        System.out.println("Total: " + lista.total());
    }
}