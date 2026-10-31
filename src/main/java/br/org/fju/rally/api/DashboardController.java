package br.org.fju.rally.api;

import br.org.fju.rally.servico.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    public RespostaDashboard consultar(@RequestParam(name = "rally_id", required = false) UUID rallyId,
                                      HttpServletRequest requisicao) {
        return dashboard.consultar(rallyId, requisicao);
    }
}
