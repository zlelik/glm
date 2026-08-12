package info.gamed.glm.service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import info.gamed.glm.config.GameProperties;
import info.gamed.glm.entity.Cell;
import info.gamed.glm.repository.CellRepository;

/**
 * Cell object service to handle batch operations.
 * @author Z@
 */
@Service
public class CellService {

    private final CellRepository cellRepository;
    private final GameProperties gameProperties;

    public CellService(CellRepository cellRepository, GameProperties gameProperties) {
        this.cellRepository = cellRepository;
        this.gameProperties = gameProperties;
    }

    public void deleteAllCells(List<Cell> cells) {

        int deleteBatchSize = gameProperties.getDeleteCellsBatchSize();

        // Extract IDs from the list of Cell objects
        List<Long> ids = cells.stream().map(Cell::getId).collect(Collectors.toList());

        // Process the IDs in chunks
        IntStream.range(0, (ids.size() + deleteBatchSize - 1) / deleteBatchSize)
                 .mapToObj(i -> ids.subList(i * deleteBatchSize, Math.min((i + 1) * deleteBatchSize, ids.size())))
                 .forEach(chunk -> cellRepository.deleteAllCellsById(chunk));
    }
}
