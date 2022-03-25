package ca.hc.jasper.service;

import java.util.UUID;

import ca.hc.jasper.domain.Invoice;
import ca.hc.jasper.repository.InvoiceRepository;
import ca.hc.jasper.service.errors.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

	@Autowired
	InvoiceRepository invoiceRepository;

	public void create(Invoice invoice) {
		invoice.setId(UUID.randomUUID());
		invoiceRepository.save(invoice);
	}

	public Invoice get(UUID id) {
		return invoiceRepository.findById(id).orElseThrow(NotFoundException::new);
	}

	public void update(Invoice invoice) {
		if (!invoiceRepository.existsById(invoice.getId())) throw new NotFoundException();
		invoiceRepository.save(invoice);
	}

	public void delete(UUID id) {
		try {
			invoiceRepository.deleteById(id);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
