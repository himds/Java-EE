package com.waterquality.service;

import com.waterquality.dao.CommuneDao;
import com.waterquality.dao.PrelevementDao;
import com.waterquality.model.Commune;
import com.waterquality.model.MapFeature;
import com.waterquality.model.MapResponse;
import com.waterquality.model.Prelevement;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CommuneService {
    private final CommuneDao communeDao = new CommuneDao();
    private final PrelevementDao prelevementDao = new PrelevementDao();
    private final WaterQualityColorService colorService = new WaterQualityColorService();

    public List<Commune> getAll(Integer year, String pollutant) throws Exception {
        List<Commune> communes = communeDao.findAll(5000);
        for (Commune commune : communes) enrich(commune, year, pollutant);
        return communes;
    }

    public List<Commune> search(String query, Integer year, String pollutant) throws Exception {
        List<Commune> communes = communeDao.search(query, 10);
        for (Commune commune : communes) enrich(commune, year, pollutant);
        return communes;
    }

    public Optional<Commune> getByCodeInsee(String codeInsee, Integer year, String pollutant) throws Exception {
        Optional<Commune> commune = communeDao.findByCodeInsee(codeInsee);
        commune.ifPresent(item -> enrich(item, year, pollutant));
        return commune;
    }

    public MapResponse getMapData(String mode, Integer year, String pollutant) throws Exception {
        if (mode == null || mode.isBlank()) mode = "regions";
        MapResponse response = new MapResponse();
        response.setMode(mode);
        response.setYear(year);
        response.setPollutant(pollutant == null || pollutant.isBlank() ? "all" : pollutant);

        List<Commune> communes = communeDao.findAll(20000);
        Map<String, Prelevement> latestByCommune = prelevementDao.findLatestByCommuneAndFilters(year, pollutant);

        List<MapFeature> features = communes.stream()
                .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
                .map(c -> toFeature(c, latestByCommune.get(c.getCodeInsee()), response.getPollutant(), year))
                .collect(Collectors.toList());

        response.setFeatures(features);
        return response;
    }

    private MapFeature toFeature(Commune commune, Prelevement prelevement, String pollutant, Integer year) {
        MapFeature f = new MapFeature();
        f.setId(commune.getCodeInsee());
        f.setType("commune");
        f.setName(commune.getNomCommune());
        f.setDepartement(commune.getDepartement());
        f.setCenterLat(commune.getLatitude());
        f.setCenterLon(commune.getLongitude());
        f.setSampleCount(prelevement == null ? 0 : 1);
        f.setColor(colorService.getColor(prelevement));
        f.setStatus(prelevement == null ? "Aucune donnée" :
                (prelevement.getConclusionprel() == null || prelevement.getConclusionprel().isBlank()
                        ? colorService.getToneName(prelevement)
                        : prelevement.getConclusionprel()));
        f.setPollutant(pollutant);
        f.setYear(year);
        return f;
    }

    private void enrich(Commune commune, Integer year, String pollutant) {
        try {
            Optional<Prelevement> latest = prelevementDao.findLatestByCommuneAndFilters(commune.getCodeInsee(), year, pollutant);
            if (latest.isPresent()) {
                commune.setColor(colorService.getColor(latest.get()));
                commune.setStatus(latest.get().getConclusionprel() == null || latest.get().getConclusionprel().isBlank()
                        ? colorService.getToneName(latest.get())
                        : latest.get().getConclusionprel());
            } else {
                commune.setColor("#9ca3af");
                commune.setStatus("Aucune donnée");
            }
        } catch (Exception e) {
            commune.setColor("#9ca3af");
            commune.setStatus("Erreur lecture");
        }
    }
}
