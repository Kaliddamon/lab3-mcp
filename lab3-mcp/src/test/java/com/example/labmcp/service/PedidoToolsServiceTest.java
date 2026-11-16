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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PedidoToolsServiceTest {

    private ProductoRepository productoRepository;
    private ClienteRepository clienteRepository;
    private PedidoRepository pedidoRepository;
    private LineaPedidoRepository lineaPedidoRepository;
    private PedidoToolsService service;

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        pedidoRepository = mock(PedidoRepository.class);
        lineaPedidoRepository = mock(LineaPedidoRepository.class);
        service = new PedidoToolsService(
                productoRepository, clienteRepository, pedidoRepository, lineaPedidoRepository);
    }

    @Test
    void buscarProductosRechazaFragmentoEnBlanco() {
        assertThrows(IllegalArgumentException.class, () -> service.buscarProductos("   "));

        verify(productoRepository, never()).findByNombreContainingIgnoreCase(any());
        verifyNoInteractions(productoRepository);
    }

    @Test
    void crearPedidoReutilizaClienteYGuardaBorrador() {
        Cliente cliente = new Cliente();
        cliente.setId(5L);
        cliente.setNombre("Ana");
        cliente.setTelefono("3001112233");
        cliente.setDireccion("Calle 1");
        when(clienteRepository.findByTelefono("3001112233")).thenReturn(Optional.of(cliente));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pedido pedido = service.crearPedido("Ana", "3001112233", "  Carrera 10  ");

        assertEquals(EstadoPedido.BORRADOR, pedido.getEstado());
        assertEquals(BigDecimal.ZERO, pedido.getTotal());
        assertEquals("Carrera 10", pedido.getDireccionEntrega());
        assertSame(cliente, pedido.getCliente());
        verify(clienteRepository, never()).save(any());
        verify(pedidoRepository).save(any(Pedido.class));
    }

    @Test
    void agregarProductoRechazaCantidadMenorQueUno() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido(1L, EstadoPedido.BORRADOR)));

        assertThrows(IllegalArgumentException.class,
                () -> service.agregarProductoAlPedido(1L, 9L, 0));

        verify(lineaPedidoRepository, never()).save(any());
    }

    @Test
    void agregarProductoRechazaCantidadMayorQueElStock() {
        Producto producto = producto(9L, "Leche entera", 3);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido(1L, EstadoPedido.BORRADOR)));
        when(productoRepository.findById(9L)).thenReturn(Optional.of(producto));
        when(lineaPedidoRepository.findByPedidoIdAndProductoId(1L, 9L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.agregarProductoAlPedido(1L, 9L, 4));

        assertEquals(3, producto.getStock().intValue());
        verify(lineaPedidoRepository, never()).save(any());
    }

    @Test
    void agregarProductoRechazaPedidoQueNoEstaEnBorrador() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido(1L, EstadoPedido.CONFIRMADO)));

        assertThrows(IllegalArgumentException.class,
                () -> service.agregarProductoAlPedido(1L, 9L, 1));

        verify(lineaPedidoRepository, never()).save(any());
    }

    @Test
    void confirmarPedidoDescuentaStockYPasaAConfirmado() {
        Producto producto = producto(9L, "Leche entera", 10);
        Pedido pedido = pedido(1L, EstadoPedido.BORRADOR);
        LineaPedido linea = linea(pedido, producto, 4, new BigDecimal("4500.00"));
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(lineaPedidoRepository.findByPedidoId(1L)).thenReturn(List.of(linea));
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pedido confirmado = service.confirmarPedido(1L);

        assertEquals(EstadoPedido.CONFIRMADO, confirmado.getEstado());
        assertEquals(6, producto.getStock().intValue());
        verify(productoRepository).save(producto);
    }

    @Test
    void confirmarPedidoNoCambiaStockSiUnaLineaSuperaElDisponible() {
        Producto producto = producto(9L, "Leche entera", 2);
        Pedido pedido = pedido(1L, EstadoPedido.BORRADOR);
        LineaPedido linea = linea(pedido, producto, 5, new BigDecimal("4500.00"));
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(lineaPedidoRepository.findByPedidoId(1L)).thenReturn(List.of(linea));

        assertThrows(IllegalArgumentException.class, () -> service.confirmarPedido(1L));

        assertEquals(2, producto.getStock().intValue());
        assertEquals(EstadoPedido.BORRADOR, pedido.getEstado());
        verify(productoRepository, never()).save(any());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void cancelarPedidoConfirmadoDevuelveElStock() {
        Producto producto = producto(9L, "Leche entera", 7);
        Pedido pedido = pedido(1L, EstadoPedido.CONFIRMADO);
        LineaPedido linea = linea(pedido, producto, 3, new BigDecimal("4500.00"));
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(lineaPedidoRepository.findByPedidoId(1L)).thenReturn(List.of(linea));
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String mensaje = service.cancelarPedido(1L);

        assertEquals(EstadoPedido.CANCELADO, pedido.getEstado());
        assertEquals(10, producto.getStock().intValue());
        assertEquals("Pedido 1 cancelado correctamente.", mensaje);
        verify(productoRepository).save(producto);
    }

    @Test
    void avanzarEntregaDesdeConfirmadoPasaAEnPreparacion() {
        Pedido pedido = pedido(1L, EstadoPedido.CONFIRMADO);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pedido avanzado = service.avanzarEntrega(1L);

        assertEquals(EstadoPedido.EN_PREPARACION, avanzado.getEstado());
        verify(pedidoRepository).save(pedido);
    }

    @Test
    void avanzarEntregaDesdeBorradorLanzaYNoGuarda() {
        Pedido pedido = pedido(2L, EstadoPedido.BORRADOR);
        when(pedidoRepository.findById(2L)).thenReturn(Optional.of(pedido));

        assertThrows(IllegalArgumentException.class, () -> service.avanzarEntrega(2L));

        assertEquals(EstadoPedido.BORRADOR, pedido.getEstado());
        verify(pedidoRepository, never()).save(any());
    }

    private Pedido pedido(Long id, EstadoPedido estado) {
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setEstado(estado);
        pedido.setDireccionEntrega("Carrera 10");
        pedido.setTotal(BigDecimal.ZERO);
        return pedido;
    }

    private Producto producto(Long id, String nombre, int stock) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setCategoria("lacteos");
        producto.setUnidad("litro");
        producto.setPrecio(new BigDecimal("4500.00"));
        producto.setStock(stock);
        return producto;
    }

    private LineaPedido linea(Pedido pedido, Producto producto, int cantidad, BigDecimal precioUnitario) {
        LineaPedido linea = new LineaPedido();
        linea.setPedido(pedido);
        linea.setProducto(producto);
        linea.setCantidad(cantidad);
        linea.setPrecioUnitario(precioUnitario);
        return linea;
    }
}
