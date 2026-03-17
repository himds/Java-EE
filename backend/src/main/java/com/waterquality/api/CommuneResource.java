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

    /**
     * 获取所有 communes
     */
    @GET
    @Path("communes")
    public Response getAll(@QueryParam("year") Integer year,
                           @QueryParam("pollutant") String pollutant) {

        try {
            List<Commune> communes = communeService.getAll(year, pollutant);
            return Response.ok(communes).build();

        } catch (Exception e) {

            // ⭐ 打印真实异常
            e.printStackTrace();

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "error", "Erreur lecture communes",
                            "message", e.getMessage()
                    ))
                    .build();
        }
    }

    /**
     * 地图数据
     */
    @GET
    @Path("map-data")
    public Response getMapData(@QueryParam("mode") String mode,
                               @QueryParam("year") Integer year,
                               @QueryParam("pollutant") String pollutant) {

        try {
            MapResponse data = communeService.getMapData(mode, year, pollutant);
            return Response.ok(data).build();

        } catch (Exception e) {

            e.printStackTrace();

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "error", "Erreur lecture carte",
                            "message", e.getMessage()
                    ))
                    .build();
        }
    }

    /**
     * 搜索 commune
     */
    @GET
    @Path("search")
    public Response search(@QueryParam("q") String query,
                           @QueryParam("year") Integer year,
                           @QueryParam("pollutant") String pollutant) {

        try {

            if (query == null || query.isBlank()) {
                return Response.ok(List.of()).build();
            }

            List<Commune> result = communeService.search(query.trim(), year, pollutant);
            return Response.ok(result).build();

        } catch (Exception e) {

            e.printStackTrace();

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "error", "Erreur recherche",
                            "message", e.getMessage()
                    ))
                    .build();
        }
    }

    /**
     * commune 详情
     */
    @GET
    @Path("communes/{id}")
    public Response getCommune(@PathParam("id") String codeInsee,
                               @QueryParam("year") Integer year,
                               @QueryParam("pollutant") String pollutant) {

        try {

            Commune commune = communeService
                    .getByCodeInsee(codeInsee, year, pollutant)
                    .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND));

            return Response.ok(commune).build();

        } catch (WebApplicationException e) {
            throw e;

        } catch (Exception e) {

            e.printStackTrace();

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "error", "Erreur détail commune",
                            "message", e.getMessage()
                    ))
                    .build();
        }
    }

    /**
     * prélèvement详情
     */
    @GET
    @Path("details/{prelevement}")
    public Response getDetails(@PathParam("prelevement") int prelevementId) {

        try {

            List<AnalysisResult> results =
                    analysisResultDao.findByPrelevementId(prelevementId);

            return Response.ok(results).build();

        } catch (Exception e) {

            e.printStackTrace();

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "error", "Erreur détail prélèvement",
                            "message", e.getMessage()
                    ))
                    .build();
        }
    }

    /**
     * 最新 prélèvement
     */
    @GET
    @Path("latest/{communeId}")
    public Response getLatestPrelevement(@PathParam("communeId") String codeInsee,
                                         @QueryParam("year") Integer year,
                                         @QueryParam("pollutant") String pollutant) {

        try {

            Prelevement prelevement = prelevementDao
                    .findLatestByCommuneAndFilters(codeInsee, year, pollutant)
                    .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND));

            prelevement.setColor(colorService.getColor(prelevement));

            return Response.ok(Map.of(
                    "prelevement", prelevement,
                    "tone", colorService.getToneName(prelevement)
            )).build();

        } catch (WebApplicationException e) {
            throw e;

        } catch (Exception e) {

            e.printStackTrace();

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "error", "Erreur dernier prélèvement",
                            "message", e.getMessage()
                    ))
                    .build();
        }
    }
}