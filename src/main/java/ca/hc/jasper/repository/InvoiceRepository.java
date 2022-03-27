package ca.hc.jasper.repository;

import java.util.UUID;

import ca.hc.jasper.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
}
