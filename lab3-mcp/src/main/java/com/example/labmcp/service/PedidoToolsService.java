package com.example.labmcp.service;

import com.example.labmcp.model.Cliente;
import com.example.labmcp.model.EstadoPedido;
import com.example.labmcp.model.LineaPedido;
import com.example.labmcp.model.Pedido;
import com.example.labmcp.model.Producto;
import com.example.labmcp.repository.ClienteRepository;
import com.example.labmcp.repository.LineaPedidoRepository;
import com.example.labmcp.repository.PedidoRepository;
import com.example.labmcp.repository.ProductoRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedidos de supermercado a domicilio expuestos como herramientas MCP.
 *
 * Cada método @Tool se publica automáticamente por el servidor MCP:
 *   - nombre      = nombre del método
 *   - descripción = atributo description
 *   - JSON schema      = derivado de los parámetros (incluye @ToolParam)
 *
 * El modelo puede invocar estos métodos cuando el usuario hable en lenguaje
 * natural: "ármame un pedido", "confirma el pedido 3", "¿en qué va mi entrega?".
 */
@Component
public class PedidoToolsService {

    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final LineaPedidoRepository lineaPedidoRepository;

    public PedidoToolsService(ProductoRepository productoRepository,
                              ClienteRepository clienteRepository,
                              PedidoRepository pedidoRepository,
                              LineaPedidoRepository lineaPedidoRepository) {
        this.productoRepository = productoRepository;
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.lineaPedidoRepository = lineaPedidoRepository;
    }

    @Tool(description = "Úsala cuando el usuario quiera ver el catálogo completo de productos del supermercado.")
    public List<Producto> listarProductos() {
        return productoRepository.findAll();
    }

    @Tool(description = "Úsala cuando el usuario busque productos por nombre o por un fragmento del nombre.")
    public List<Producto> buscarProductos(
            @ToolParam(description = "Fragmento del nombre del producto. No puede estar vacío") String fragmento) {
        String texto = textoObligatorio(fragmento, "El fragmento de búsqueda es obligatorio");
        return productoRepository.findByNombreContainingIgnoreCase(texto);
    }

    @Tool(description = "Úsala cuando el usuario pida los productos de una categoría: lacteos, abarrotes, frutas, aseo o bebidas.")
    public List<Producto> listarProductosPorCategoria(
            @ToolParam(description = "Categoría del catálogo, por ejemplo lacteos o bebidas") String categoria) {
        String texto = textoObligatorio(categoria, "La categoría es obligatoria");
        return productoRepository.findByCategoriaIgnoreCase(texto);
    }

    @Tool(description = "Úsala cuando el usuario quiera abrir un pedido a domicilio. Si el teléfono ya existe, reutiliza ese cliente y deja el pedido en BORRADOR.")
    @Transactional
    public Pedido crearPedido(
            @ToolParam(description = "Nombre del cliente") String nombre,
            @ToolParam(description = "Teléfono del cliente. Identifica al cliente existente") String telefono,
            @ToolParam(description = "Dirección donde se entrega el pedido") String direccionEntrega) {
        String nombreLimpio = textoObligatorio(nombre, "El nombre del cliente es obligatorio");
        String telefonoLimpio = textoObligatorio(telefono, "El teléfono es obligatorio");
        String direccion = textoObligatorio(direccionEntrega, "La dirección de entrega es obligatoria");

        Cliente cliente = clienteRepository.findByTelefono(telefonoLimpio).orElseGet(() -> {
            Cliente nuevo = new Cliente();
            nuevo.setNombre(nombreLimpio);
            nuevo.setTelefono(telefonoLimpio);
            nuevo.setDireccion(direccion);
            return clienteRepository.save(nuevo);
        });

        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setDireccionEntrega(direccion);
        pedido.setEstado(EstadoPedido.BORRADOR);
        pedido.setTotal(BigDecimal.ZERO);
        return pedidoRepository.save(pedido);
    }

    @Tool(description = "Úsala cuando el usuario quiera añadir unidades de un producto a un pedido que todavía está en BORRADOR. Respeta el stock y no cambia el precio ya guardado en la línea.")
    @Transactional
    public Pedido agregarProductoAlPedido(
            @ToolParam(description = "Id del pedido en borrador") Long pedidoId,
            @ToolParam(description = "Id del producto del catálogo") Long productoId,
            @ToolParam(description = "Unidades a sumar. Debe ser mayor o igual a 1") Integer cantidad) {
        Pedido pedido = requirePedido(pedidoId);
        asegurarBorrador(pedido);
        if (cantidad == null || cantidad < 1) {
            throw new IllegalArgumentException("La cantidad debe ser mayor o igual a 1");
        }
        Producto producto = requireProducto(productoId);

        LineaPedido linea = lineaPedidoRepository.findByPedidoIdAndProductoId(pedidoId, productoId).orElse(null);
        int nuevaCantidad = cantidad;
        if (linea != null) {
            nuevaCantidad = cantidadDeLinea(linea) + cantidad;
        }
        int stock = stockDe(producto);
        if (nuevaCantidad > stock) {
            throw new IllegalArgumentException(
                    "No hay stock suficiente de " + producto.getNombre()
                            + ". Disponible: " + stock + ", solicitado: " + nuevaCantidad);
        }

        if (linea == null) {
            linea = new LineaPedido();
            linea.setPedido(pedido);
            linea.setProducto(producto);
            linea.setCantidad(nuevaCantidad);
            linea.setPrecioUnitario(producto.getPrecio());
        } else {
            linea.setCantidad(nuevaCantidad);
        }
        lineaPedidoRepository.save(linea);
        recalcularTotal(pedido);
        return pedido;
    }

    @Tool(description = "Úsala cuando el usuario quiera quitar un producto de un pedido que todavía está en BORRADOR.")
    @Transactional
    public Pedido quitarProductoDelPedido(
            @ToolParam(description = "Id del pedido en borrador") Long pedidoId,
            @ToolParam(description = "Id del producto que se quita del pedido") Long productoId) {
        Pedido pedido = requirePedido(pedidoId);
        asegurarBorrador(pedido);
        if (productoId == null) {
            throw new IllegalArgumentException("El id del producto es obligatorio");
        }
        LineaPedido linea = lineaPedidoRepository.findByPedidoIdAndProductoId(pedidoId, productoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El producto " + productoId + " no está en el pedido " + pedidoId));
        lineaPedidoRepository.delete(linea);
        recalcularTotal(pedido);
        return pedido;
    }

    @Tool(description = "Úsala cuando el usuario quiera ver el detalle de un pedido: cliente, dirección, estado, total y líneas, sin devolver el grafo de entidades.")
    public DetallePedido verPedido(
            @ToolParam(description = "Id del pedido") Long pedidoId) {
        Pedido pedido = requirePedido(pedidoId);
        List<LineaResumen> resumenes = new ArrayList<>();
        for (LineaPedido linea : lineasDe(pedidoId)) {
            Producto producto = productoDeLinea(linea);
            int cantidad = cantidadDeLinea(linea);
            BigDecimal precio = linea.getPrecioUnitario();
            if (precio == null) {
                throw new IllegalArgumentException("La línea no tiene precio unitario");
            }
            resumenes.add(new LineaResumen(
                    producto.getId(),
                    producto.getNombre(),
                    cantidad,
                    precio,
                    precio.multiply(BigDecimal.valueOf(cantidad))));
        }
        Cliente cliente = pedido.getCliente();
        if (cliente == null) {
            throw new IllegalArgumentException("El pedido " + pedidoId + " no tiene cliente");
        }
        return new DetallePedido(
                pedido.getId(),
                pedido.getEstado(),
                pedido.getDireccionEntrega(),
                cliente.getNombre(),
                pedido.getTotal(),
                resumenes);
    }

    @Tool(description = "Úsala cuando el usuario confirme el pedido. Solo aplica a un BORRADOR con líneas y descuenta el stock de cada producto.")
    @Transactional
    public Pedido confirmarPedido(
            @ToolParam(description = "Id del pedido en borrador que se confirma") Long pedidoId) {
        Pedido pedido = requirePedido(pedidoId);
        asegurarBorrador(pedido);
        List<LineaPedido> lineas = lineasDe(pedidoId);
        if (lineas.isEmpty()) {
            throw new IllegalArgumentException("No se puede confirmar un pedido sin productos");
        }
        for (LineaPedido linea : lineas) {
            Producto producto = productoDeLinea(linea);
            int cantidad = cantidadDeLinea(linea);
            int stock = stockDe(producto);
            if (cantidad > stock) {
                throw new IllegalArgumentException(
                        "No hay stock suficiente para confirmar el pedido. El producto "
                                + producto.getNombre() + " tiene " + stock
                                + " y el pedido pide " + cantidad);
            }
        }
        for (LineaPedido linea : lineas) {
            Producto producto = linea.getProducto();
            producto.setStock(producto.getStock() - linea.getCantidad());
            productoRepository.save(producto);
        }
        pedido.setEstado(EstadoPedido.CONFIRMADO);
        return pedidoRepository.save(pedido);
    }

    @Tool(description = "Úsala cuando el usuario quiera cancelar un pedido. Si el stock ya se descontó, lo devuelve. Rechaza un pedido entregado o que ya está cancelado.")
    @Transactional
    public String cancelarPedido(
            @ToolParam(description = "Id del pedido a cancelar") Long pedidoId) {
        Pedido pedido = requirePedido(pedidoId);
        EstadoPedido estado = pedido.getEstado();
        if (estado == EstadoPedido.ENTREGADO) {
            throw new IllegalArgumentException(
                    "No se puede cancelar el pedido " + pedido.getId() + " porque ya fue entregado");
        }
        if (estado == EstadoPedido.CANCELADO) {
            throw new IllegalArgumentException("El pedido " + pedido.getId() + " ya está cancelado");
        }
        if (estado == EstadoPedido.CONFIRMADO
                || estado == EstadoPedido.EN_PREPARACION
                || estado == EstadoPedido.EN_CAMINO) {
            for (LineaPedido linea : lineasDe(pedido.getId())) {
                Producto producto = productoDeLinea(linea);
                producto.setStock(stockDe(producto) + cantidadDeLinea(linea));
                productoRepository.save(producto);
            }
        } else if (estado != EstadoPedido.BORRADOR) {
            throw new IllegalArgumentException(
                    "No se puede cancelar el pedido " + pedido.getId() + " en estado " + estado);
        }
        pedido.setEstado(EstadoPedido.CANCELADO);
        pedidoRepository.save(pedido);
        return "Pedido " + pedido.getId() + " cancelado correctamente.";
    }

    @Tool(description = "Úsala cuando el usuario pregunte en qué estado está un pedido.")
    public String consultarEstadoPedido(
            @ToolParam(description = "Id del pedido") Long pedidoId) {
        Pedido pedido = requirePedido(pedidoId);
        return "El pedido " + pedido.getId() + " está en estado " + pedido.getEstado();
    }

    @Tool(description = "Úsala cuando el usuario avance la entrega, solo en la cadena CONFIRMADO, EN_PREPARACION, EN_CAMINO y ENTREGADO.")
    @Transactional
    public Pedido avanzarEntrega(
            @ToolParam(description = "Id del pedido cuya entrega avanza un paso") Long pedidoId) {
        Pedido pedido = requirePedido(pedidoId);
        EstadoPedido estado = pedido.getEstado();
        EstadoPedido siguiente;
        if (estado == EstadoPedido.CONFIRMADO) {
            siguiente = EstadoPedido.EN_PREPARACION;
        } else if (estado == EstadoPedido.EN_PREPARACION) {
            siguiente = EstadoPedido.EN_CAMINO;
        } else if (estado == EstadoPedido.EN_CAMINO) {
            siguiente = EstadoPedido.ENTREGADO;
        } else {
            throw new IllegalArgumentException(
                    "No se puede avanzar la entrega del pedido " + pedido.getId()
                            + " en estado " + estado
                            + ". La secuencia es CONFIRMADO, EN_PREPARACION, EN_CAMINO y ENTREGADO");
        }
        pedido.setEstado(siguiente);
        return pedidoRepository.save(pedido);
    }

    private Pedido requirePedido(Long pedidoId) {
        if (pedidoId == null) {
            throw new IllegalArgumentException("El id del pedido es obligatorio");
        }
        return pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el pedido " + pedidoId));
    }

    private Producto requireProducto(Long productoId) {
        if (productoId == null) {
            throw new IllegalArgumentException("El id del producto es obligatorio");
        }
        return productoRepository.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el producto " + productoId));
    }

    private void recalcularTotal(Pedido pedido) {
        BigDecimal total = BigDecimal.ZERO;
        for (LineaPedido linea : lineasDe(pedido.getId())) {
            BigDecimal precio = linea.getPrecioUnitario();
            if (precio == null) {
                throw new IllegalArgumentException("La línea no tiene precio unitario");
            }
            total = total.add(precio.multiply(BigDecimal.valueOf(cantidadDeLinea(linea))));
        }
        pedido.setTotal(total);
        pedidoRepository.save(pedido);
    }

    private void asegurarBorrador(Pedido pedido) {
        if (pedido.getEstado() != EstadoPedido.BORRADOR) {
            throw new IllegalArgumentException(
                    "El pedido " + pedido.getId() + " está en estado " + pedido.getEstado()
                            + ". Solo se puede modificar un pedido en BORRADOR");
        }
    }

    private List<LineaPedido> lineasDe(Long pedidoId) {
        List<LineaPedido> lineas = lineaPedidoRepository.findByPedidoId(pedidoId);
        if (lineas == null) {
            return List.of();
        }
        return lineas;
    }

    private Producto productoDeLinea(LineaPedido linea) {
        Producto producto = linea.getProducto();
        if (producto == null) {
            throw new IllegalArgumentException("La línea no tiene un producto asociado");
        }
        return producto;
    }

    private int cantidadDeLinea(LineaPedido linea) {
        if (linea.getCantidad() == null) {
            throw new IllegalArgumentException("La línea no tiene cantidad");
        }
        return linea.getCantidad();
    }

    private int stockDe(Producto producto) {
        if (producto.getStock() == null) {
            throw new IllegalArgumentException("El producto " + producto.getId() + " no tiene stock");
        }
        return producto.getStock();
    }

    private String textoObligatorio(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.trim();
    }

    public record DetallePedido(
            Long pedidoId,
            EstadoPedido estado,
            String direccionEntrega,
            String nombreCliente,
            BigDecimal total,
            List<LineaResumen> lineas) {
    }

    public record LineaResumen(
            Long productoId,
            String nombre,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal) {
    }
}
