package com.waterquality.api;

import com.waterquality.dao.AnalysisResultDao;
import com.waterquality.dao.PrelevementDao;
import com.waterquality.model.AnalysisResult;
import com.waterquality.model.Commune;
import com.waterquality.model.MapResponse;
import com.waterquality.model.Prelevement;
import com.waterquality.service.CommuneService;
import com.waterquality.service.WaterQualityColorService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class CommuneResource {
    private final CommuneService communeService = new CommuneService();
    private final PrelevementDao prelevementDao = new PrelevementDao();
    private final AnalysisResultDao analysisResultDao = new AnalysisResultDao();
    private final WaterQualityColorService colorService = new WaterQualityColorService();

    @GET
    @Path("communes")
    public List<Commune> getAll(@QueryParam("year") Integer year,
                                @QueryParam("pollutant") String pollutant) {
        try {
            return communeService.getAll(year, pollutant);
        } catch (Exception e) {
            throw new WebApplicationException("Erreur lecture communes", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("map-data")
    public MapResponse getMapData(@QueryParam("mode") String mode,
                                  @QueryParam("year") Integer year,
                                  @QueryParam("pollutant") String pollutant) {
        try {
            return communeService.getMapData(mode, year, pollutant);
        } catch (Exception e) {
            throw new WebApplicationException("Erreur lecture carte", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("search")
    public List<Commune> search(@QueryParam("q") String query,
                                @QueryParam("year") Integer year,
                                @QueryParam("pollutant") String pollutant) {
        try {
            if (query == null || query.isBlank()) return List.of();
            return communeService.search(query.trim(), year, pollutant);
        } catch (Exception e) {
            throw new WebApplicationException("Erreur recherche", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("communes/{id}")
    public Commune getCommune(@PathParam("id") String codeInsee,
                              @QueryParam("year") Integer year,
                              @QueryParam("pollutant") String pollutant) {
        try {
            return communeService.getByCodeInsee(codeInsee, year, pollutant)
                    .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND));
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException("Erreur détail commune", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("details/{prelevement}")
    public List<AnalysisResult> getDetails(@PathParam("prelevement") int prelevementId) {
        try {
            return analysisResultDao.findByPrelevementId(prelevementId);
        } catch (Exception e) {
            throw new WebApplicationException("Erreur détail prélèvement", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("latest/{communeId}")
    public Response getLatestPrelevement(@PathParam("communeId") String codeInsee,
                                         @QueryParam("year") Integer year,
                                         @QueryParam("pollutant") String pollutant) {
        try {
            Prelevement prelevement = prelevementDao.findLatestByCommuneAndFilters(codeInsee, year, pollutant)
                    .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND));
            prelevement.setColor(colorService.getColor(prelevement));
            return Response.ok(Map.of("prelevement", prelevement, "tone", colorService.getToneName(prelevement))).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException("Erreur dernier prélèvement", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}
