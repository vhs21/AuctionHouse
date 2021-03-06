package com.epam.auction.receiver.impl;

import com.epam.auction.controller.RequestContent;
import com.epam.auction.dao.impl.DAOFactory;
import com.epam.auction.dao.ItemDAO;
import com.epam.auction.dao.PhotoDAO;
import com.epam.auction.dao.criteria.FilterCriteria;
import com.epam.auction.dao.criteria.FilterType;
import com.epam.auction.dao.criteria.OrderCriteria;
import com.epam.auction.db.DAOManager;
import com.epam.auction.entity.DeliveryStatus;
import com.epam.auction.entity.Item;
import com.epam.auction.entity.ItemStatus;
import com.epam.auction.entity.Photo;
import com.epam.auction.entity.User;
import com.epam.auction.exception.DAOException;
import com.epam.auction.exception.MethodNotSupportedException;
import com.epam.auction.exception.PhotoLoadingException;
import com.epam.auction.exception.ReceiverException;
import com.epam.auction.exception.WrongFilterParameterException;
import com.epam.auction.receiver.ItemReceiver;
import com.epam.auction.receiver.RequestConstant;
import com.epam.auction.util.SiteManager;
import com.epam.auction.util.DateFixer;
import com.epam.auction.util.PhotoLoader;
import com.epam.auction.validator.ItemValidator;
import com.epam.auction.validator.PhotoValidator;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;

class ItemReceiverImpl implements ItemReceiver {

    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void createItem(RequestContent requestContent) throws ReceiverException {
        Item item = new Item(
                requestContent.getRequestParameter(RequestConstant.TITLE)[0],
                requestContent.getRequestParameter(RequestConstant.DESCRIPTION)[0],
                new BigDecimal(requestContent.getRequestParameter(RequestConstant.START_PRICE)[0]),
                new BigDecimal(requestContent.getRequestParameter(RequestConstant.BLITZ_PRICE)[0]),
                Date.valueOf(requestContent.getRequestParameter(RequestConstant.START_DATE)[0]),
                Date.valueOf(requestContent.getRequestParameter(RequestConstant.CLOSE_DATE)[0]),
                Integer.valueOf(requestContent.getRequestParameter(RequestConstant.CATEGORY)[0]),
                ((User) requestContent.getSessionAttribute(RequestConstant.USER)).getId());

        ItemValidator itemValidator = new ItemValidator();
        if (itemValidator.validateItemParam(item)) {
            Map<String, InputStream> files = requestContent.getFiles();

            ItemDAO itemDAO = DAOFactory.getInstance().getItemDAO();
            PhotoDAO photoDAO = DAOFactory.getInstance().getPhotoDAO();

            DAOManager daoManager = new DAOManager(true, itemDAO, photoDAO);
            daoManager.beginTransaction();
            try {
                itemDAO.create(item);

                if (files != null) {
                    savePhotos(photoDAO, files, item.getId());
                }

                daoManager.commit();
            } catch (DAOException | MethodNotSupportedException | PhotoLoadingException e) {
                daoManager.rollback();
                throw new ReceiverException(e);
            } finally {
                daoManager.endTransaction();
            }
        } else {
            throw new ReceiverException(itemValidator.getValidationMessage());
        }
    }

    @Override
    public void loadItem(RequestContent requestContent) throws ReceiverException {
        int itemId = Integer.valueOf(requestContent.getRequestParameter(RequestConstant.ITEM_ID)[0]);

        ItemDAO itemDAO = DAOFactory.getInstance().getItemDAO();

        try (DAOManager daoManager = new DAOManager(itemDAO)) {
            requestContent.setSessionAttribute(RequestConstant.ITEM,
                    itemDAO.findEntityById(itemId));
        } catch (DAOException e) {
            throw new ReceiverException(e);
        }
    }

    @Override
    public void updateItem(RequestContent requestContent) throws ReceiverException {
        String newTitle = requestContent.getRequestParameter(RequestConstant.TITLE)[0];
        String newDescription = requestContent.getRequestParameter(RequestConstant.DESCRIPTION)[0];
        BigDecimal newStartPrice = new BigDecimal(requestContent.getRequestParameter(RequestConstant.START_PRICE)[0]);
        BigDecimal newBlitzPrice = new BigDecimal(requestContent.getRequestParameter(RequestConstant.BLITZ_PRICE)[0]);
        Date newStartDate = Date.valueOf(requestContent.getRequestParameter(RequestConstant.START_DATE)[0]);
        Date newCloseDate = Date.valueOf(requestContent.getRequestParameter(RequestConstant.CLOSE_DATE)[0]);

        ItemValidator itemValidator = new ItemValidator();

        if (itemValidator.validateItemParam(newTitle, newDescription,
                newStartPrice, newBlitzPrice, newStartDate, newCloseDate)) {
            Item item = (Item) requestContent.getSessionAttribute(RequestConstant.ITEM);
            item.setName(newTitle);
            item.setDescription(newDescription);
            item.setStartPrice(newStartPrice);
            item.setBlitzPrice(newBlitzPrice);
            item.setStartDate(newStartDate);
            item.setCloseDate(newCloseDate);

            ItemDAO itemDAO = DAOFactory.getInstance().getItemDAO();
            DAOManager daoManager = new DAOManager(true, itemDAO);

            daoManager.beginTransaction();
            try {
                updateItemForCheck(item, itemDAO);
                daoManager.commit();
            } catch (DAOException | MethodNotSupportedException e) {
                daoManager.rollback();
                throw new ReceiverException(e);
            } finally {
                daoManager.endTransaction();
            }
        } else {
            throw new ReceiverException(itemValidator.getValidationMessage());
        }
    }

    @Override
    public void addPhotos(RequestContent requestContent) throws ReceiverException {
        Item item = (Item) requestContent.getSessionAttribute(RequestConstant.ITEM);

        Map<String, InputStream> files = requestContent.getFiles();
        if (files != null) {

            PhotoDAO photoDAO = DAOFactory.getInstance().getPhotoDAO();
            ItemDAO itemDAO = DAOFactory.getInstance().getItemDAO();
            DAOManager daoManager = new DAOManager(true, photoDAO, itemDAO);

            daoManager.beginTransaction();
            try {
                savePhotos(photoDAO, files, item.getId());
                updateItemForCheck(item, itemDAO);
                daoManager.commit();
            } catch (DAOException | MethodNotSupportedException | PhotoLoadingException e) {
                daoManager.rollback();
                throw new ReceiverException(e);
            } finally {
                daoManager.endTransaction();
            }
        }
    }

    @Override
    public void deleteItem(RequestContent requestContent) throws ReceiverException {
        long itemId = ((Item) requestContent.getSessionAttribute(RequestConstant.ITEM)).getId();

        ItemDAO itemDAO = DAOFactory.getInstance().getItemDAO();
        PhotoDAO photoDAO = DAOFactory.getInstance().getPhotoDAO();
        DAOManager daoManager = new DAOManager(true, itemDAO, photoDAO);

        daoManager.beginTransaction();
        try {
            photoDAO.deleteItemPhotos(itemId);
            itemDAO.delete(itemId);
            daoManager.commit();
        } catch (DAOException | MethodNotSupportedException e) {
            daoManager.rollback();
            throw new ReceiverException(e);
        } finally {
            daoManager.endTransaction();
        }
    }

    @Override
    public void cancelAuction(RequestContent requestContent) throws ReceiverException {
        updateItemStatus(requestContent, ItemStatus.CANCELED);
    }

    @Override
    public void approveItem(RequestContent requestContent) throws ReceiverException {
        updateItemStatus(requestContent, ItemStatus.CONFIRMED);
    }

    @Override
    public void discardItem(RequestContent requestContent) throws ReceiverException {
        updateItemStatus(requestContent, ItemStatus.NOT_CONFIRMED);
    }

    @Override
    public void loadItemsForCheck(RequestContent requestContent) throws ReceiverException {
        loadItemsWithStatus(requestContent, ItemStatus.CREATED);
    }

    @Override
    public void loadActiveItems(RequestContent requestContent) throws ReceiverException {
        loadItemsWithStatus(requestContent, ItemStatus.ACTIVE);
    }

    @Override
    public void loadComingItems(RequestContent requestContent) throws ReceiverException {
        loadItemsWithStatus(requestContent, ItemStatus.CONFIRMED);
    }

    @Override
    public void loadPurchasedItems(RequestContent requestContent) throws ReceiverException {
        User user = (User) requestContent.getSessionAttribute(RequestConstant.USER);
        if (user != null) {

            ItemDAO itemDAO = DAOFactory.getInstance().getItemDAO();

            try (DAOManager daoManager = new DAOManager(itemDAO)) {
                FilterCriteria filterCriteria = extractFilter(requestContent);
                OrderCriteria orderCriteria = extractOrderParameters(requestContent);

                PaginationHelper paginationHelper = new PaginationHelper(SiteManager.getInstance().getItemsForPage());
                paginationHelper.definePage(requestContent);
                if (paginationHelper.pagesNumberNotDefined(requestContent)) {
                    paginationHelper.definePages(requestContent, itemDAO.countRows(user.getId(), filterCriteria));
                }

                List<Item> items = itemDAO.findPurchasedItems(user.getId(), filterCriteria, orderCriteria,
                        paginationHelper.findOffset(), paginationHelper.getLimit());

                requestContent.setRequestAttribute(RequestConstant.ITEMS, items);
            } catch (DAOException e) {
                throw new ReceiverException(e);
            }
        }
    }

    @Override
    public void loadUserItems(RequestContent requestContent) throws ReceiverException {
        User user = (User) requestContent.getSessionAttribute(RequestConstant.USER);
        if (user != null) {
            FilterCriteria filterCriteria = extractFilter(requestContent);
            try {
                filterCriteria.put(FilterType.SELLER_ID, user.getId());
                loadItems(requestContent, filterCriteria, extractOrderParameters(requestContent));
            } catch (WrongFilterParameterException e) {
                throw new ReceiverException(e);
            }
        }
    }

    @Override
    public void searchItems(RequestContent requestContent) throws ReceiverException {
        FilterCriteria filterCriteria = (FilterCriteria) requestContent.getSessionAttribute(RequestConstant.FILTER_CRITERIA);
        OrderCriteria orderCriteria = (OrderCriteria) requestContent.getSessionAttribute(RequestConstant.ORDER_CRITERIA);
        filterCriteria.put(FilterType.SEARCH_NAME,
                requestContent.getRequestParameter(RequestConstant.SEARCH_NAME)[0]);
        loadItems(requestContent, filterCriteria, orderCriteria);
    }

    @Override
    public void confirmDelivery(RequestContent requestContent) throws ReceiverException {
        Item item = (Item) requestContent.getSessionAttribute(RequestConstant.ITEM);
        DeliveryStatus itemDeliveryStatus = item.getDeliveryStatus();

        if (Boolean.valueOf(requestContent.getRequestParameter(RequestConstant.IS_SELLER)[0])) {
            if (DeliveryStatus.BUYER_C.equals(itemDeliveryStatus)) {
                updateDeliveryStatus(item, DeliveryStatus.SELLER_BUYER_C);
            } else if (DeliveryStatus.BUYER_RV.equals(itemDeliveryStatus)) {
                updateDeliveryStatus(item, DeliveryStatus.SELLER_C_BUYER_RV);
            } else if (DeliveryStatus.NO_DELIVERY.equals(itemDeliveryStatus)) {
                updateDeliveryStatus(item, DeliveryStatus.SELLER_C);
            }
        } else {
            if (DeliveryStatus.SELLER_C.equals(itemDeliveryStatus)) {
                updateDeliveryStatus(item, DeliveryStatus.SELLER_BUYER_C);
            } else if (DeliveryStatus.SELLER_RV.equals(itemDeliveryStatus)) {
                updateDeliveryStatus(item, DeliveryStatus.SELLER_RV_BUYER_C);
            } else if (DeliveryStatus.NO_DELIVERY.equals(itemDeliveryStatus)) {
                updateDeliveryStatus(item, DeliveryStatus.BUYER_C);
            }
        }
    }

    @Override
    public void reportViolation(RequestContent requestContent) throws ReceiverException {
        Item item = (Item) requestContent.getSessionAttribute(RequestConstant.ITEM);
        DeliveryStatus itemDeliveryStatus = item.getDeliveryStatus();

        if (Boolean.valueOf(requestContent.getRequestParameter(RequestConstant.IS_SELLER)[0])) {
            if (DeliveryStatus.BUYER_RV.equals(itemDeliveryStatus)) {
                updateDeliveryStatus(item, DeliveryStatus.SELLER_BUYER_RV);
            } else if (DeliveryStatus.BUYER_C.equals(itemDeliveryStatus)) {
                updateDeliveryStatus(item, DeliveryStatus.SELLER_RV_BUYER_C);
            } else if (DeliveryStatus.NO_DELIVERY.equals(itemDeliveryStatus)) {
                updateDeliveryStatus(item, DeliveryStatus.SELLER_RV);
            }
        } else {
            if (DeliveryStatus.SELLER_RV.equals(itemDeliveryStatus)) {
                updateDeliveryStatus(item, DeliveryStatus.SELLER_BUYER_RV);
            } else if (DeliveryStatus.SELLER_C.equals(itemDeliveryStatus)) {
                updateDeliveryStatus(item, DeliveryStatus.SELLER_C_BUYER_RV);
            } else if (DeliveryStatus.NO_DELIVERY.equals(itemDeliveryStatus)) {
                updateDeliveryStatus(item, DeliveryStatus.BUYER_RV);
            }
        }
    }

    private FilterCriteria extractFilter(RequestContent requestContent) {
        FilterCriteria filterCriteria = new FilterCriteria();

        if (requestContent.getRequestParameter(RequestConstant.INITIAL) == null) {
            for (FilterType filterType : FilterType.values()) {
                String[] requestParameter = requestContent
                        .getRequestParameter(filterType.name().toLowerCase().replaceAll("_", "-"));
                if (requestParameter != null && !requestParameter[0].isEmpty()) {
                    filterCriteria.put(filterType, requestParameter[0]);
                }
            }
            requestContent.setSessionAttribute(RequestConstant.FILTER_CRITERIA, filterCriteria);

        } else {
            filterCriteria = (FilterCriteria) requestContent.getSessionAttribute(RequestConstant.FILTER_CRITERIA);
        }

        return filterCriteria;
    }

    private OrderCriteria extractOrderParameters(RequestContent requestContent) {
        OrderCriteria orderCriteria;

        if (requestContent.getRequestParameter(RequestConstant.INITIAL) == null) {
            String[] orderBy = requestContent.getRequestParameter(RequestConstant.ORDER_BY);
            String[] orderType = requestContent.getRequestParameter(RequestConstant.ORDER_TYPE);

            if (orderBy != null && orderType != null) {
                orderCriteria = new OrderCriteria(orderBy[0], orderType[0]);
            } else if (orderBy != null) {
                orderCriteria = new OrderCriteria(orderBy[0]);
            } else {
                orderCriteria = new OrderCriteria();
            }
            requestContent.setSessionAttribute(RequestConstant.ORDER_CRITERIA, orderCriteria);
        } else {
            orderCriteria = (OrderCriteria) requestContent.getSessionAttribute(RequestConstant.ORDER_CRITERIA);
        }

        return orderCriteria;
    }

    private void loadItemsWithStatus(RequestContent requestContent, ItemStatus itemStatus) throws ReceiverException {
        FilterCriteria filterCriteria = extractFilter(requestContent);
        try {
            filterCriteria.put(FilterType.STATUS, itemStatus.ordinal());
            loadItems(requestContent, filterCriteria, extractOrderParameters(requestContent));
        } catch (WrongFilterParameterException e) {
            throw new ReceiverException(e);
        }
    }

    private void loadItems(RequestContent requestContent, FilterCriteria filterCriteria, OrderCriteria orderCriteria)
            throws ReceiverException {
        ItemDAO itemDAO = DAOFactory.getInstance().getItemDAO();

        try (DAOManager daoManager = new DAOManager(itemDAO)) {

            PaginationHelper paginationHelper = new PaginationHelper(SiteManager.getInstance().getItemsForPage());
            paginationHelper.definePage(requestContent);
            if (paginationHelper.pagesNumberNotDefined(requestContent)) {
                paginationHelper.definePages(requestContent, itemDAO.countRows(filterCriteria));
            }

            List<Item> items = itemDAO.findItemsWithFilter(filterCriteria, orderCriteria,
                    paginationHelper.findOffset(), paginationHelper.getLimit());

            requestContent.setRequestAttribute(RequestConstant.ITEMS, items);
        } catch (DAOException e) {
            throw new ReceiverException(e);
        }
    }

    private void updateItemStatus(RequestContent requestContent, ItemStatus itemStatus) throws ReceiverException {
        Item item = (Item) requestContent.getSessionAttribute(RequestConstant.ITEM);

        ItemDAO itemDAO = DAOFactory.getInstance().getItemDAO();

        DAOManager daoManager = new DAOManager(true, itemDAO);
        daoManager.beginTransaction();

        try {
            itemDAO.updateItemStatus(item.getId(), itemStatus);
            item.setStatus(itemStatus);
            daoManager.commit();
        } catch (DAOException e) {
            daoManager.rollback();
            throw new ReceiverException(e);
        } finally {
            daoManager.endTransaction();
        }
    }

    private void updateDeliveryStatus(Item item, DeliveryStatus deliveryStatus) throws ReceiverException {
        ItemDAO itemDAO = DAOFactory.getInstance().getItemDAO();

        DAOManager daoManager = new DAOManager(true, itemDAO);
        daoManager.beginTransaction();

        try {
            itemDAO.updateDeliveryStatus(item.getId(), deliveryStatus);
            item.setDeliveryStatus(deliveryStatus);
            daoManager.commit();
        } catch (DAOException e) {
            throw new ReceiverException(e);
        }
    }

    private void updateItemForCheck(Item item, ItemDAO itemDAO) throws MethodNotSupportedException, DAOException {
        ItemValidator itemValidator = new ItemValidator();
        if (!itemValidator.validateStartDate(item.getStartDate())) {
            item.setStartDate(DateFixer.addDays(2));
        }
        if (!itemValidator.validateCloseDate(item.getCloseDate(), item.getStartDate())) {
            item.setCloseDate(DateFixer.addDays(item.getStartDate(), 2));
        }
        item.setStatus(ItemStatus.CREATED);
        itemDAO.update(item);
    }

    private void savePhotos(PhotoDAO photoDAO, Map<String, InputStream> files, long itemId)
            throws DAOException, MethodNotSupportedException, PhotoLoadingException {
        PhotoLoader photoLoader = new PhotoLoader();
        PhotoValidator photoValidator = new PhotoValidator();

        int i = 0;
        for (Map.Entry<String, InputStream> file : files.entrySet()) {
            if (photoValidator.validatePhotoExtension(file.getKey())) {
                photoDAO.create(new Photo(photoLoader.savePhotoToServer(file.getValue(), i++), itemId));
            } else {
                LOGGER.log(Level.WARN, photoValidator.getValidationMessage());
            }
        }

    }

}