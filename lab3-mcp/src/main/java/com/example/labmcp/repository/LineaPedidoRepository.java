package com.example.labmcp.repository;

import com.example.labmcp.model.LineaPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LineaPedidoRepository extends JpaRepository<LineaPedido, Long> {

    List<LineaPedido> findByPedidoId(Long pedidoId);

    Optional<LineaPedido> findByPedidoIdAndProductoId(Long pedidoId, Long productoId);
}
