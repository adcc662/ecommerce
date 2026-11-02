-- At most one active (pending or completed) payment per order, even under concurrent requests.
CREATE UNIQUE INDEX ux_payments_order_active
    ON payments (order_id)
    WHERE payment_status IN ('PENDING', 'COMPLETED');
