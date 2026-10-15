package br.com.estudos.edital;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.estudos.shared.enums.StatusAnaliseEdital;

public interface AnaliseEditalRepository extends JpaRepository<AnaliseEdital, Long> {

    List<AnaliseEdital> findTop10ByOrderByCriadoEmDescIdDesc();

    List<AnaliseEdital> findByStatusIn(Collection<StatusAnaliseEdital> status);
}
